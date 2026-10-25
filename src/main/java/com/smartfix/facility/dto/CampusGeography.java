package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;
import java.util.List;
import java.util.Map;

/** Server-to-server location mapping; location ids are not exposed by the map status endpoint. */
public record CampusGeography(List<CampusBuilding> buildings, String verifiedOn,
        Map<Long, String> locationBuildings, List<FacilityCount> facilityCounts) {
    // Own the collection structure; preserve existing null/element semantics.
    public CampusGeography {
        buildings = buildings == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(buildings));
        locationBuildings = locationBuildings == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(locationBuildings));
        facilityCounts = facilityCounts == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(facilityCounts));
    }

    public record FacilityCount(String buildingId, FacilityStatus status, long count) {}
}
