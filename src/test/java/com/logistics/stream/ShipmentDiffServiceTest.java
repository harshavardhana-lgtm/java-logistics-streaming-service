package com.logistics.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.logistics.stream.model.Shipment;
import com.logistics.stream.model.ShipmentDiff;
import com.logistics.stream.service.ShipmentDiffService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipmentDiffServiceTest {

    private final ShipmentDiffService service = new ShipmentDiffService();

    private static Shipment shipment(
            String id, String vessel, String cargoClass, String origin, String destination,
            String status, double lat, double lon, int eta, int confidence, int teu) {
        return new Shipment(id, vessel, cargoClass, origin, destination, status, lat, lon, eta,
                confidence, teu, 1_700_000_000_000L);
    }

    private static Shipment base(String id) {
        return shipment(id, "MAERSK EDMUND", "container", "SGSIN", "NLRTM", "IN_TRANSIT",
                1.25, 103.75, 7, 90, 4200);
    }

    @Test
    void detectsNoChangeWhenNothingMoves() {
        List<Shipment> current = List.of(base("SHP-1"), base("SHP-2"));
        List<Shipment> next = List.of(base("SHP-1"), base("SHP-2"));
        ShipmentDiff diff = service.diff(current, next, 1L);
        assertThat(diff.addedCount()).isZero();
        assertThat(diff.updatedCount()).isZero();
        assertThat(diff.removedCount()).isZero();
    }

    @Test
    void detectsAddedShipment() {
        List<Shipment> current = List.of(base("SHP-1"));
        List<Shipment> next = List.of(base("SHP-1"), base("SHP-2"));
        ShipmentDiff diff = service.diff(current, next, 1L);
        assertThat(diff.addedCount()).isEqualTo(1);
        assertThat(diff.added().get(0).id()).isEqualTo("SHP-2");
    }

    @Test
    void detectsRemovedShipment() {
        List<Shipment> current = List.of(base("SHP-1"), base("SHP-2"));
        List<Shipment> next = List.of(base("SHP-1"));
        ShipmentDiff diff = service.diff(current, next, 1L);
        assertThat(diff.removedCount()).isEqualTo(1);
        assertThat(diff.removed()).containsExactly("SHP-2");
    }

    @Test
    void detectsStatusChange() {
        Shipment moved = shipment("SHP-1", "MAERSK EDMUND", "container", "SGSIN", "NLRTM",
                "DELAYED", 1.25, 103.75, 7, 90, 4200);
        ShipmentDiff diff = service.diff(List.of(base("SHP-1")), List.of(moved), 1L);
        assertThat(diff.updatedCount()).isEqualTo(1);
    }

    @Test
    void detectsPositionChange() {
        Shipment moved = shipment("SHP-1", "MAERSK EDMUND", "container", "SGSIN", "NLRTM",
                "IN_TRANSIT", 1.30, 103.75, 7, 90, 4200);
        ShipmentDiff diff = service.diff(List.of(base("SHP-1")), List.of(moved), 1L);
        assertThat(diff.updatedCount()).isEqualTo(1);
    }

    @Test
    void ignoresSubCentimetreCoordinateNoise() {
        Shipment noisy = shipment("SHP-1", "MAERSK EDMUND", "container", "SGSIN", "NLRTM",
                "IN_TRANSIT", 1.2501, 103.7501, 7, 90, 4200);
        ShipmentDiff diff = service.diff(List.of(base("SHP-1")), List.of(noisy), 1L);
        assertThat(diff.updatedCount()).isZero();
    }

    @Test
    void normalisesNullAndWhitespaceFields() {
        Shipment blank = shipment("SHP-1", "  MAERSK EDMUND  ", "container", "SGSIN", "NLRTM",
                "IN_TRANSIT", 1.25, 103.75, 7, 90, 4200);
        ShipmentDiff diff = service.diff(List.of(base("SHP-1")), List.of(blank), 1L);
        assertThat(diff.updatedCount()).isZero();
    }

    @Test
    void versionIsMonotonic() {
        long first = service.diff(List.of(), List.of(base("SHP-1")), 1L).version();
        long second = service.diff(List.of(), List.of(base("SHP-2")), 1L).version();
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void handlesEmptyCurrentState() {
        ShipmentDiff diff = service.diff(List.of(), List.of(base("SHP-1")), 1L);
        assertThat(diff.addedCount()).isEqualTo(1);
        assertThat(diff.removedCount()).isZero();
    }
}
