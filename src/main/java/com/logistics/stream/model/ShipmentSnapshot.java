package com.logistics.stream.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentSnapshot(
        @JsonProperty("version") long version,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("count") int count,
        @JsonProperty("shipments") List<Shipment> shipments) {
}
