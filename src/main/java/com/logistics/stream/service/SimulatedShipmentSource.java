package com.logistics.stream.service;

import com.logistics.stream.model.Shipment;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "logistics.source",
        havingValue = "simulated",
        matchIfMissing = true)
public class SimulatedShipmentSource implements ShipmentSource {

    private static final String[] PORTS = {
        "SGSIN", "NLRTM", "DEHAM", "CNSHA", "INNSA", "AEJEA", "USLAX", "BRSSZ", "ZADUR", "KRPUS"
    };

    private static final String[] VESSELS = {
        "MAERSK EDMUND", "EVER GIVEN", "CMA CGM RIVOLI", "MSC OSCAR", "ONE INNOVATION",
        "HAPAG LYRA", "COSCO GLORY", "HMM ALGECIRAS"
    };

    private static final String[] CARGO = {
        "container", "refrigerated", "bulk", "breakbulk", "hazmat", "vehicle-carrier"
    };

    private static final String[] STATUS = {
        "SCHEDULED", "LOADING", "IN_TRANSIT", "AT_ANCHORAGE", "DELAYED",
        "CUSTOMS_HOLD", "DISCHARGING", "DELIVERED"
    };

    private final Random random = new Random(42L);
    private volatile int tick = 0;

    @Override
    public List<Shipment> fetch() {
        tick++;
        int count = 40 + random.nextInt(60);
        List<Shipment> shipments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = "SHP-" + (1000 + i);
            String status = STATUS[random.nextInt(STATUS.length)];
            if (tick % 7 == 0 && i % 11 == 0) {
                status = "DELAYED";
            }
            shipments.add(
                    new Shipment(
                            id,
                            VESSELS[random.nextInt(VESSELS.length)],
                            CARGO[random.nextInt(CARGO.length)],
                            PORTS[random.nextInt(PORTS.length)],
                            PORTS[random.nextInt(PORTS.length)],
                            status,
                            round(-60 + random.nextDouble() * 130),
                            round(-179 + random.nextDouble() * 358),
                            random.nextInt(21),
                            55 + random.nextInt(45),
                            100 + random.nextInt(8000,
                                    random.nextInt(400) == 0 ? 20000 : 9000),
                            System.currentTimeMillis()));
        }
        return shipments;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
