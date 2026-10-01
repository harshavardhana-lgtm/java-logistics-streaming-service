package com.logistics.stream.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Shipment(
        @JsonProperty("id") String id,
        @JsonProperty("vessel") String vessel,
        @JsonProperty("cargoClass") String cargoClass,
        @JsonProperty("originPort") String originPort,
        @JsonProperty("destinationPort") String destinationPort,
        @JsonProperty("status") String status,
        @JsonProperty("lat") double lat,
        @JsonProperty("lon") double lon,
        @JsonProperty("etaDays") int etaDays,
        @JsonProperty("confidence") int confidence,
        @JsonProperty("teu") int teu,
        @JsonProperty("lastEventAt") long lastEventAt) {
}
