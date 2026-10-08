package com.smartfix.dispatch.service;

import com.smartfix.dispatch.dto.TechnicianRecommendationResponse;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.technician.service.TechnicianWorkloadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TechnicianRecommendationService {
    // Sprint 3 section 14.3: availability first, then workload, then profile id for stable ties.
    // Keep database calls out of the comparator: each candidate's workload is read exactly once.
    private static final Comparator<TechnicianRecommendationResponse> RANKING =
            Comparator.comparingInt((TechnicianRecommendationResponse candidate) ->
                            candidate.availabilityStatus() == AvailabilityStatus.AVAILABLE ? 0 : 1)
                    .thenComparingLong(TechnicianRecommendationResponse::openWorkOrders)
                    .thenComparing(TechnicianRecommendationResponse::profileId);

    private final TechnicianDirectoryService directory;
    private final TechnicianWorkloadService workload;

    public TechnicianRecommendationService(TechnicianDirectoryService directory, TechnicianWorkloadService workload) {
        this.directory = directory;
        this.workload = workload;
    }

    /**
     * Returns only F1-F5 eligible candidates, with real workload and deterministic ordering.
     * This read-only recommendation does not reserve or assign a technician; the assignment
     * transaction must recheck eligibility when the administrator submits their choice.
     * A workload failure propagates instead of publishing a partial or zero-filled ranking.
     */
    public List<TechnicianRecommendationResponse> recommend(MaintenanceCategory category, Long locationId) {
        return directory.findCandidates(category, locationId).stream()
                .map(candidate -> new TechnicianRecommendationResponse(
                        candidate.profileId(), candidate.userId(), candidate.displayName(),
                        candidate.skills(), candidate.serviceAreaIds(), candidate.availabilityStatus(),
                        workload.countOpenWorkOrders(candidate.userId())))
                .sorted(RANKING)
                .toList();
    }
}
