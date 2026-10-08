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
    public long getOperationalCount() { return statusCounts.getOrDefault(FacilityStatus.OPERATIONAL, 0L); }
    public long getMaintenanceCount() { return statusCounts.getOrDefault(FacilityStatus.UNDER_MAINTENANCE, 0L); }
    public long getOutOfServiceCount() { return statusCounts.getOrDefault(FacilityStatus.OUT_OF_SERVICE, 0L); }
}
