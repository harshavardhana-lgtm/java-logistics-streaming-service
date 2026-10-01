package com.logistics.stream.ws;

import tools.jackson.databind.ObjectMapper;
import com.logistics.stream.model.ShipmentDiff;
import com.logistics.stream.model.ShipmentDiffEvent;
import com.logistics.stream.model.ShipmentSnapshot;
import com.logistics.stream.service.ShipmentPoller;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ShipmentStreamHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStreamHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ShipmentPoller poller;
    private final ExecutorService writers;
    private final ScheduledExecutorService maintenance;

    private final int outboxLimit;
    private final long staleAfterMs;
    private final int maxSessions;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Outbox> outboxes = new ConcurrentHashMap<>();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public ShipmentStreamHandler(
            ShipmentPoller poller,
            @Value("${logistics.outbox-limit:256}") int outboxLimit,
            @Value("${logistics.stale-after-ms:10000}") long staleAfterMs,
            @Value("${logistics.max-sessions:2000}") int maxSessions,
            @Value("${logistics.writer-threads:8}") int writerThreads) {
        this.poller = poller;
        this.outboxLimit = outboxLimit;
        this.staleAfterMs = staleAfterMs;
        this.maxSessions = maxSessions;
        this.writers = Executors.newFixedThreadPool(Math.max(1, writerThreads));
        this.maintenance =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread t = new Thread(runnable, "outbox-maintenance");
                            t.setDaemon(true);
                            return t;
                        });
        this.maintenance.scheduleWithFixedDelay(this::sweep, 1, 1, TimeUnit.SECONDS);
    }

    private final class Outbox {
        private final java.util.concurrent.ConcurrentLinkedQueue<String> queue =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        private volatile long lastProgress = System.currentTimeMillis();
        private volatile int size = 0;

        boolean offer(String payload) {
            if (size >= outboxLimit) {
                dropped.incrementAndGet();
                return false;
            }
            queue.offer(payload);
            size++;
            return true;
        }

        String poll() {
            final String head = queue.poll();
            if (head != null) {
                size--;
            }
            return head;
        }

        void touch() {
            lastProgress = System.currentTimeMillis();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (sessions.size() >= maxSessions) {
            rejected.incrementAndGet();
            log.warn("rejecting session {}: at capacity {}", session.getId(), maxSessions);
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        sessions.put(session.getId(), session);
        outboxes.put(session.getId(), new Outbox());
        accepted.incrementAndGet();

        ShipmentSnapshot snapshot = poller.snapshot();
        if (snapshot.count() > 0) {
            enqueue(session.getId(), snapshotFrame(snapshot));
        }
        log.info("session {} opened, {} active", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {
        final ClientMessage inbound;
        try {
            inbound = mapper.readValue(message.getPayload(), ClientMessage.class);
        } catch (Exception e) {
            log.debug("unparseable frame from {}: {}", session.getId(), e.toString());
            enqueue(session.getId(), "{\"type\":\"error\",\"reason\":\"bad_frame\"}");
            return;
        }
        if (inbound == null || inbound.type() == null) {
            return;
        }
        switch (inbound.type()) {
            case "resync" -> enqueue(session.getId(), snapshotFrame(poller.snapshot()));
            case "ping" -> enqueue(session.getId(), "{\"type\":\"pong\"}");
            case "stats" -> enqueue(session.getId(), statsFrame());
            default -> log.debug("unknown frame type {} from {}", inbound.type(), session.getId());
        }
    }

    private String statsFrame() {
        return "{\"type\":\"stats\",\"activeSessions\":" + sessions.size()
                + ",\"droppedMessages\":" + dropped.get() + ",\"evictedSessions\":" + evicted.get()
                + ",\"version\":" + poller.snapshot().version() + "}";
    }

    private void enqueue(String id, String payload) {
        Outbox outbox = outboxes.get(id);
        if (outbox == null) {
            return;
        }
        if (outbox.offer(payload)) {
            writers.submit(() -> drain(id));
        }
    }

    private void drain(String id) {
        WebSocketSession session = sessions.get(id);
        Outbox outbox = outboxes.get(id);
        if (session == null || outbox == null || !session.isOpen()) {
            return;
        }
        String head;
        while ((head = outbox.poll()) != null) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(head));
                }
                outbox.touch();
            } catch (IOException e) {
                log.debug("session {} send failed: {}", id, e.toString());
                outbox.touch();
                return;
            }
        }
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        outboxes.forEach(
                (id, outbox) -> {
                    if (now - outbox.lastProgress > staleAfterMs) {
                        log.info("evicting stale session {} (idle {} ms)", id, now - outbox.lastProgress);
                        evict(id);
                    }
                });
    }

    private void evict(String id) {
        WebSocketSession session = sessions.remove(id);
        outboxes.remove(id);
        if (session != null) {
            evicted.incrementAndGet();
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        outboxes.remove(session.getId());
        log.info("session {} closed ({}), {} active", session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("session {} transport error: {}", session.getId(), exception.toString());
        sessions.remove(session.getId());
        outboxes.remove(session.getId());
    }

    @EventListener
    public void onDiff(ShipmentDiffEvent event) {
        broadcast(event.diff());
    }

    public void broadcast(ShipmentDiff diff) {
        if (diff == null) {
            return;
        }
        final String payload;
        try {
            payload =
                    "{\"type\":\"diff\",\"version\":"
                            + diff.version()
                            + ",\"payload\":"
                            + mapper.writeValueAsString(diff)
                            + "}";
        } catch (Exception e) {
            log.warn("failed to encode diff: {}", e.toString());
            return;
        }
        for (String id : sessions.keySet()) {
            enqueue(id, payload);
        }
    }

    private String snapshotFrame(ShipmentSnapshot snapshot) throws IOException {
        return "{\"type\":\"snapshot\",\"version\":"
                + snapshot.version()
                + ",\"payload\":"
                + mapper.writeValueAsString(snapshot)
                + "}";
    }

    public int activeSessions() {
        return sessions.size();
    }

    public long acceptedCount() {
        return accepted.get();
    }

    public long rejectedCount() {
        return rejected.get();
    }

    public long evictedCount() {
        return evicted.get();
    }

    public long droppedCount() {
        return dropped.get();
    }
}
