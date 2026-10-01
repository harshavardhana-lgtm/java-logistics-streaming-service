package com.logistics.stream.service;

import com.logistics.stream.model.Shipment;
import com.logistics.stream.model.ShipmentDiff;
import com.logistics.stream.model.ShipmentDiffEvent;
import com.logistics.stream.model.ShipmentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ShipmentPoller {

    private static final Logger log = LoggerFactory.getLogger(ShipmentPoller.class);

    private final ShipmentSource source;
    private final ShipmentDiffService diffService;
    private final ApplicationEventPublisher events;
    private final Map<String, Shipment> cache = new ConcurrentHashMap<>();
    private final AtomicLong polls = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile ShipmentSnapshot lastSnapshot = new ShipmentSnapshot(0L, 0L, 0, List.of());

    public ShipmentPoller(
            ShipmentSource source,
            ShipmentDiffService diffService,
            ApplicationEventPublisher events) {
        this.source = source;
        this.diffService = diffService;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${logistics.poll-interval-ms:500}")
    public ShipmentDiff poll() {
        long pollNumber = polls.incrementAndGet();
        List<Shipment> incoming;
        try {
            incoming = source.fetch();
        } catch (RuntimeException e) {
            failures.incrementAndGet();
            log.warn("shipment poll {} failed: {}", pollNumber, e.toString());
            return null;
        }

        List<Shipment> current = new ArrayList<>(cache.values());
        ShipmentDiff diff = diffService.diff(current, incoming, System.currentTimeMillis());
        if (diff.addedCount() == 0 && diff.updatedCount() == 0 && diff.removedCount() == 0) {
            return null;
        }

        for (Shipment s : diff.added()) {
            cache.put(s.id(), s);
        }
        for (Shipment s : diff.updated()) {
            cache.put(s.id(), s);
        }
        for (String id : diff.removed()) {
            cache.remove(id);
        }

        lastSnapshot =
                new ShipmentSnapshot(diff.version(), diff.timestamp(), cache.size(), snapshotOf(cache));
        log.info(
                "poll {} produced version {} (+{} ~{} -{}) cache={}",
                pollNumber,
                diff.version(),
                diff.addedCount(),
                diff.updatedCount(),
                diff.removedCount(),
                cache.size());
        events.publishEvent(new ShipmentDiffEvent(diff));
        return diff;
    }

    private List<Shipment> snapshotOf(Map<String, Shipment> map) {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public ShipmentSnapshot snapshot() {
        return lastSnapshot;
    }

    public int cacheSize() {
        return cache.size();
    }

    public long pollCount() {
        return polls.get();
    }

    public long failureCount() {
        return failures.get();
    }
}
