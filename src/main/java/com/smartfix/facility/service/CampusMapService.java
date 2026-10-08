package com.smartfix.facility.service;

import com.smartfix.facility.domain.Facility;
import com.smartfix.facility.domain.Location;
import com.smartfix.facility.dto.CampusMapFacilityResponse;
import com.smartfix.facility.dto.CampusMapBuildingResponse;
import com.smartfix.facility.dto.CampusMapResponse;
import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.facility.repository.FacilityRepository;
import com.smartfix.facility.repository.LocationRepository;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class CampusMapService {

    private final FacilityRepository facilityRepository;
    private final LocationRepository locations;
    private final UserService users;

    public CampusMapService(FacilityRepository facilityRepository, LocationRepository locations,
                            UserService users) {
        this.facilityRepository = facilityRepository;
        this.locations = locations;
        this.users = users;
    }

    /** Build the directory from real data; filter only fields the caller is allowed to see. */
    @Transactional(readOnly = true)
    public CampusMapResponse browse(Long actorId, String query, String building, String floor,
                                    FacilityStatus status, int page, int size) {
        boolean details = requireActiveActor(actorId) == Role.ADMINISTRATOR;
        String search = checked(query, 100).toLowerCase(Locale.ROOT);
        String selectedBuilding = checked(building, 100);
        String selectedFloor = checked(floor, 20);
        var activeLocations = locations.findAllByActiveTrueOrderByDisplayNameAsc();
        var rows = facilityRepository.findByLocationActiveTrueOrderByNameAscIdAsc().stream()
                .map(facility -> toResponse(facility, details))
                .sorted(Comparator.comparing(CampusMapFacilityResponse::building, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CampusMapFacilityResponse::floor, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CampusMapFacilityResponse::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CampusMapFacilityResponse::id))
                .toList();

        Map<String, List<Location>> groups = activeLocations.stream().collect(Collectors.groupingBy(
                location -> buildingName(location.getBuilding()), TreeMap::new, Collectors.toList()));
        Map<String, Long> facilityCounts = rows.stream().collect(Collectors.groupingBy(
                CampusMapFacilityResponse::building, Collectors.counting()));
        var buildings = groups.entrySet().stream().map(group -> new CampusMapBuildingResponse(
                group.getKey(), group.getValue().size(),
                facilityCounts.getOrDefault(group.getKey(), 0L),
                group.getValue().stream().filter(location -> location.getFloor() != null && !location.getFloor().isBlank())
                        .map(location -> floorName(location.getFloor()))
                        .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList())).toList();
        var floors = activeLocations.stream()
                .filter(location -> selectedBuilding.isEmpty()
                        || buildingName(location.getBuilding()).equals(selectedBuilding))
                .map(location -> floorName(location.getFloor())).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        var area = rows.stream()
                .filter(row -> selectedBuilding.isEmpty() || row.building().equals(selectedBuilding))
                .filter(row -> selectedFloor.isEmpty() || row.floor().equals(selectedFloor))
                .filter(row -> search.isEmpty() || searchable(row).contains(search)).toList();
        Map<FacilityStatus, Long> counts = new EnumMap<>(FacilityStatus.class);
        for (FacilityStatus value : FacilityStatus.values()) {
            counts.put(value, area.stream().filter(row -> row.status() == value).count());
        }
        var matching = area.stream().filter(row -> status == null || row.status() == status).toList();
        int pageSize = Math.min(48, size <= 0 ? 12 : size);
        int pageNumber = Math.max(0, Math.min(page, Math.max(0, (matching.size() - 1) / pageSize)));
        int from = pageNumber * pageSize;
        var found = new PageImpl<>(matching.subList(from, Math.min(from + pageSize, matching.size())),
                PageRequest.of(pageNumber, pageSize), matching.size());
        return new CampusMapResponse(found, buildings, floors, activeLocations.size(), rows.size(), counts);
    }

    Role requireActiveActor(Long actorId) {
        if (actorId == null) { throw new AccessDeniedException("Sign in to view campus facilities."); }
        try {
            var actor = users.getUserAccess(actorId);
            if (actor.accountStatus() != AccountStatus.ACTIVE) {
                throw new AccessDeniedException("An active account is required.");
            }
            return actor.role();
        } catch (ResourceNotFoundException absent) {
            throw new AccessDeniedException("An active account is required.");
        }
    }

    private static String checked(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) { throw new InputValidationException("The map filter is too long."); }
        return clean;
    }

    private static String buildingName(String value) {
        return value == null || value.isBlank() ? "Building not specified" : value.trim();
    }

    private static String floorName(String value) {
        return value == null || value.isBlank() ? "Floor not specified" : value.trim();
    }

    private static String searchable(CampusMapFacilityResponse row) {
        return (row.name() + " " + row.building() + " " + row.floor() + " "
                + row.locationDisplayName() + " " + (row.room() == null ? "" : row.room()))
                .toLowerCase(Locale.ROOT);
    }

    private CampusMapFacilityResponse toResponse(Facility facility, boolean details) {
        Location location = facility.getLocation();

        return new CampusMapFacilityResponse(
            facility.getId(),
            facility.getName(),
            facility.getStatus(),
            location.getId(),
            details ? location.getDisplayName()
                    : buildingName(location.getBuilding()) + " · " + floorName(location.getFloor()),
            buildingName(location.getBuilding()),
            floorName(location.getFloor()),
            details ? location.getRoom() : null);
    }
}
