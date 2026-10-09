package com.smartfix.facility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfix.facility.dto.CampusBuilding;
import com.smartfix.facility.dto.CampusGeography;
import com.smartfix.facility.repository.FacilityRepository;
import com.smartfix.facility.repository.LocationRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Local, reviewed public snapshot: runtime requests never crawl third-party directories. */
@Service
public class CampusGeographyService {
    private final Catalogue catalogue;
    private final Map<String, String> aliases;
    private final CampusMapService access;
    private final LocationRepository locations;
    private final FacilityRepository facilities;

    public CampusGeographyService(ObjectMapper mapper, CampusMapService access,
            LocationRepository locations, FacilityRepository facilities) throws IOException {
        this.access = access;
        this.locations = locations;
        this.facilities = facilities;
        try (var input = new ClassPathResource("data/nus-campus-buildings.json").getInputStream()) {
            catalogue = mapper.readValue(input, Catalogue.class);
        }
        Set<String> ids = new HashSet<>();
        Map<String, Set<String>> candidates = new HashMap<>();
        for (var building : catalogue.buildings()) {
            if (!ids.add(building.id()) || building.latitude() < 1.14 || building.latitude() > 1.50
                    || building.longitude() < 103.5 || building.longitude() > 104.51
                    || !Double.isFinite(building.latitude()) || !Double.isFinite(building.longitude())) {
                throw new IllegalStateException("Invalid NUS building catalogue: " + building.id());
            }
            for (String alias : concat(building.name(), building.aliases())) {
                candidates.computeIfAbsent(normalize(alias), ignored -> new HashSet<>()).add(building.id());
            }
        }
        aliases = new HashMap<>();
        // Ambiguous names intentionally stay unmapped rather than selecting the wrong campus/block.
        candidates.forEach((name, matches) -> {
            if (matches.size() == 1) aliases.put(name, matches.iterator().next());
        });
    }

    @Transactional(readOnly = true)
    public CampusGeography read(Long actorId) {
        access.requireActiveActor(actorId);
        Map<Long, String> addresses = new HashMap<>();
        for (var location : locations.findAllByActiveTrueOrderByDisplayNameAsc()) {
            String building = aliases.get(normalize(location.getBuilding()));
            if (building != null) addresses.put(location.getId(), building);
        }
        var counts = facilities.findByLocationActiveTrueOrderByNameAscIdAsc().stream()
                .collect(Collectors.groupingBy(facility -> new Key(
                        addresses.get(facility.getLocation().getId()), facility.getStatus()),
                        Collectors.counting()));
        var summaries = counts.entrySet().stream().map(entry -> new CampusGeography.FacilityCount(
                entry.getKey().buildingId(), entry.getKey().status(), entry.getValue())).toList();
        return new CampusGeography(catalogue.buildings(), catalogue.verifiedOn(),
                Map.copyOf(addresses), summaries);
    }

    private static List<String> concat(String name, List<String> values) {
        var all = new ArrayList<>(values); all.add(name); return all;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record Key(String buildingId, com.smartfix.facility.domain.FacilityStatus status) {}
    private record Catalogue(String verifiedOn, List<CampusBuilding> buildings) {}
}
