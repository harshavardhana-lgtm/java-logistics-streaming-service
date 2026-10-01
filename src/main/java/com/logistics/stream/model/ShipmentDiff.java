package com.logistics.stream.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentDiff(
        @JsonProperty("version") long version,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("addedCount") int addedCount,
        @JsonProperty("updatedCount") int updatedCount,
        @JsonProperty("removedCount") int removedCount,
        @JsonProperty("added") java.util.List<Shipment> added,
        @JsonProperty("updated") java.util.List<Shipment> updated,
        @JsonProperty("removed") java.util.List<String> removed) {
}
