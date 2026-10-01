package com.logistics.stream.service;

import com.logistics.stream.model.Shipment;
import com.logistics.stream.model.ShipmentDiff;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ShipmentDiffService {

    private static final int FIELD_COUNT = 12;

    private final AtomicLong version = new AtomicLong(0L);

    public long currentVersion() {
        return version.get();
    }

    private static boolean sameVessel(Shipment a, Shipment b) {
        return Objects.equals(norm(a.vessel()), norm(b.vessel()));
    }

    private static boolean sameCargoClass(Shipment a, Shipment b) {
        return Objects.equals(norm(a.cargoClass()), norm(b.cargoClass()));
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public boolean changed(Shipment a, Shipment b) {
        if (a == null || b == null) {
            return true;
        }
        return !sameVessel(a, b)
                || !sameCargoClass(a, b)
                || !Objects.equals(norm(a.originPort()), norm(b.originPort()))
                || !Objects.equals(norm(a.destinationPort()), norm(b.destinationPort()))
                || !Objects.equals(norm(a.status()), norm(b.status()))
                || round2(a.lat()) != round2(b.lat())
                || round2(a.lon()) != round2(b.lon())
                || a.etaDays() != b.etaDays()
                || a.confidence() != b.confidence()
                || a.teu() != b.teu();
    }

    public ShipmentDiff diff(List<Shipment> current, List<Shipment> next, long timestamp) {
        Set<String> currentIds = new LinkedHashSet<>();
        Set<String> nextIds = new LinkedHashSet<>();
        for (Shipment s : current) {
            currentIds.add(s.id());
        }
        for (Shipment s : next) {
            nextIds.add(s.id());
        }

        List<Shipment> added = new ArrayList<>();
        for (Shipment s : next) {
            if (!currentIds.contains(s.id())) {
                added.add(s);
            }
        }

        List<Shipment> removed = new ArrayList<>();
        for (Shipment s : current) {
            if (!nextIds.contains(s.id())) {
                removed.add(s);
            }
        }

        List<Shipment> updated = new ArrayList<>();
        for (Shipment before : current) {
            for (Shipment after : next) {
                if (before.id().equals(after.id()) && changed(before, after)) {
                    updated.add(after);
                    break;
                }
            }
        }

        return new ShipmentDiff(
                version.incrementAndGet(),
                timestamp,
                added.size(),
                updated.size(),
                removed.size(),
                added,
                updated,
                removed.stream().map(Shipment::id).toList());
    }

    public int fieldCount() {
        return FIELD_COUNT;
    }
}
