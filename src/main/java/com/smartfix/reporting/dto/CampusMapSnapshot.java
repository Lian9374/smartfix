package com.smartfix.reporting.dto;

import java.time.Instant;
import java.util.List;

/** Deliberately only building-level counts: no ticket, person, room or location ids. */
public record CampusMapSnapshot(Instant updatedAt, String catalogueVerifiedOn, List<Building> buildings,
                               long unmappedRequests, long unmappedFacilities) {
    // Own the collection structure; preserve existing null/element semantics.
    public CampusMapSnapshot {
        buildings = buildings == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(buildings));
    }

    public record Building(String id, String name, String campus, double latitude, double longitude,
                           List<String> aliases, long faultReports, long repairs,
                           long outOfServiceFacilities, long maintenanceFacilities,
                           long operationalFacilities) {
        // Own the collection structure; preserve existing null/element semantics.
        public Building {
            aliases = aliases == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(aliases));
        }

        public boolean hasFault() { return faultReports > 0 || outOfServiceFacilities > 0; }
        public boolean hasRepair() { return repairs > 0 || maintenanceFacilities > 0; }
        public boolean hasActivity() { return hasFault() || hasRepair(); }
    }
    public long getFaultBuildings() { return buildings.stream().filter(Building::hasFault).count(); }
    public long getRepairBuildings() { return buildings.stream().filter(Building::hasRepair).count(); }
    public List<Building> getActiveBuildings() { return buildings.stream().filter(Building::hasActivity).toList(); }
}
