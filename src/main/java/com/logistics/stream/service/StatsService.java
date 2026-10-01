package com.logistics.stream.service;

import com.logistics.stream.ws.ShipmentStreamHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private final ShipmentPoller poller;
    private final ShipmentStreamHandler handler;

    public StatsService(ShipmentPoller poller, ShipmentStreamHandler handler) {
        this.poller = poller;
        this.handler = handler;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("polls", poller.pollCount());
        stats.put("pollFailures", poller.failureCount());
        stats.put("cacheSize", poller.cacheSize());
        stats.put("version", poller.snapshot().version());
        stats.put("activeSessions", handler.activeSessions());
        stats.put("acceptedSessions", handler.acceptedCount());
        stats.put("rejectedSessions", handler.rejectedCount());
        stats.put("evictedSessions", handler.evictedCount());
        stats.put("droppedMessages", handler.droppedCount());
        return stats;
    }
}
