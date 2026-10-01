package com.logistics.stream.service;

import com.logistics.stream.model.Shipment;
import java.util.List;

public interface ShipmentSource {
    List<Shipment> fetch();
}
