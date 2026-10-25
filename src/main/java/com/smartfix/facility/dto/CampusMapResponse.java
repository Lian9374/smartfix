package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;

/** Safe view model: no entities, facility descriptions, request details or internal notes. */
public record CampusMapResponse(
        Page<CampusMapFacilityResponse> facilities,
        List<CampusMapBuildingResponse> buildings,
        List<String> floors,
        long activeLocationCount,
        long facilityCount,
        Map<FacilityStatus, Long> statusCounts) {
    // Own the collection structure; preserve existing null/element semantics.
    public CampusMapResponse {
        buildings = buildings == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(buildings));
        floors = floors == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(floors));
        statusCounts = statusCounts == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(statusCounts));
    }

    public long getOperationalCount() { return statusCounts.getOrDefault(FacilityStatus.OPERATIONAL, 0L); }
    public long getMaintenanceCount() { return statusCounts.getOrDefault(FacilityStatus.UNDER_MAINTENANCE, 0L); }
    public long getOutOfServiceCount() { return statusCounts.getOrDefault(FacilityStatus.OUT_OF_SERVICE, 0L); }
}
