package com.smartfix.facility.dto;

import java.util.List;

/** Verified public geographic point; no indoor addresses, people or operational details. */
public record CampusBuilding(String id, String name, String campus, double latitude,
                             double longitude, List<String> aliases, List<String> sources) {
    // Own the collection structure; preserve existing null/element semantics.
    public CampusBuilding {
        aliases = aliases == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(aliases));
        sources = sources == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(sources));
    }
}
