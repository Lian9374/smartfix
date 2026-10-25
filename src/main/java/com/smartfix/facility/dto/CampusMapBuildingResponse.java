package com.smartfix.facility.dto;

import java.util.List;

/** A directory entry derived only from active, persisted campus locations. */
public record CampusMapBuildingResponse(
        String name, long locationCount, long facilityCount, List<String> floors) {
    // Own the collection structure; preserve existing null/element semantics.
    public CampusMapBuildingResponse {
        floors = floors == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(floors));
    }
}
