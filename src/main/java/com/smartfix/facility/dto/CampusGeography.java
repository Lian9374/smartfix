package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;
import java.util.List;
import java.util.Map;

/** Server-to-server location mapping; location ids are not exposed by the map status endpoint. */
public record CampusGeography(List<CampusBuilding> buildings, String verifiedOn,
        Map<Long, String> locationBuildings, List<FacilityCount> facilityCounts) {
    public record FacilityCount(String buildingId, FacilityStatus status, long count) {}
}
