package com.smartfix.reporting.service;

import com.smartfix.facility.service.CampusGeographyService;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.service.RequestReadService;
import com.smartfix.reporting.dto.CampusMapSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Reporting orchestrates public read services to avoid a facility/request dependency cycle. */
@Service
public class CampusMapStatusService {
    private final CampusGeographyService geography;
    private final RequestReadService requests;

    public CampusMapStatusService(CampusGeographyService geography, RequestReadService requests) {
        this.geography = geography; this.requests = requests;
    }

    @Transactional(readOnly = true)
    public CampusMapSnapshot snapshot(Long actorId) {
        var campus = geography.read(actorId); // verifies the active account before any request read
        Map<String, Counts> counts = new HashMap<>();
        campus.buildings().forEach(building -> counts.put(building.id(), new Counts()));
        long unmappedRequests = 0, unmappedFacilities = 0;
        for (var group : requests.activeMapCounts()) {
            var count = counts.get(campus.locationBuildings().get(group.locationId()));
            if (count == null) { unmappedRequests += group.count(); continue; }
            // ASSIGNED remains an outstanding fault until work actually starts.
            if (group.status() == RequestStatus.IN_PROGRESS) count.repairs += group.count();
            else count.faultReports += group.count();
        }
        for (var group : campus.facilityCounts()) {
            var count = counts.get(group.buildingId());
            if (count == null) { unmappedFacilities += group.count(); continue; }
            switch (group.status()) {
                case OPERATIONAL -> count.operational += group.count();
                case UNDER_MAINTENANCE -> count.maintenance += group.count();
                case OUT_OF_SERVICE -> count.outOfService += group.count();
            }
        }
        var buildings = campus.buildings().stream().map(building -> {
            var count = counts.get(building.id());
            return new CampusMapSnapshot.Building(building.id(), building.name(), building.campus(),
                    building.latitude(), building.longitude(), building.aliases(), count.faultReports,
                    count.repairs, count.outOfService, count.maintenance, count.operational);
        }).toList();
        return new CampusMapSnapshot(Instant.now(), campus.verifiedOn(), buildings,
                unmappedRequests, unmappedFacilities);
    }

    private static class Counts { long faultReports, repairs, operational, maintenance, outOfService; }
}
