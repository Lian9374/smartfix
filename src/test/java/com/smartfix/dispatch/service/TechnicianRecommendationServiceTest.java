package com.smartfix.dispatch.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.dispatch.dto.TechnicianRecommendationResponse;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.TechnicianCandidateResponse;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.technician.service.TechnicianWorkloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicianRecommendationServiceTest {
    @Mock TechnicianDirectoryService directory;
    @Mock TechnicianWorkloadService workload;
    @InjectMocks TechnicianRecommendationService recommendations;

    @Test
    void sortsByAvailabilityThenWorkloadThenProfileIdAndReadsEachAccountOnce() {
        var candidates = List.of(candidate(40, 101, AvailabilityStatus.BUSY),
                candidate(30, 103, AvailabilityStatus.AVAILABLE),
                candidate(20, 105, AvailabilityStatus.AVAILABLE),
                candidate(10, 109, AvailabilityStatus.AVAILABLE));
        when(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 5L)).thenReturn(candidates);
        when(workload.countOpenWorkOrders(101L)).thenReturn(0L);
        when(workload.countOpenWorkOrders(103L)).thenReturn(1L);
        when(workload.countOpenWorkOrders(105L)).thenReturn(3L);
        when(workload.countOpenWorkOrders(109L)).thenReturn(3L);

        var result = recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L);

        assertThat(result).extracting(TechnicianRecommendationResponse::profileId).containsExactly(30L, 10L, 20L, 40L);
        assertThat(result).extracting(TechnicianRecommendationResponse::openWorkOrders).containsExactly(1L, 3L, 3L, 0L);
        assertThat(result.getFirst().userId()).isEqualTo(103L);
        assertThat(result.getFirst().displayName()).isEqualTo("Technician 103");
        assertThat(result.getFirst().skills()).containsExactly(MaintenanceCategory.ELECTRICAL);
        assertThat(result.getFirst().serviceAreaIds()).containsExactly(5L);
        for (var candidate : candidates) verify(workload, times(1)).countOpenWorkOrders(candidate.userId());
        verifyNoMoreInteractions(workload);
    }

    @Test
    void tiedCandidatesHaveTheSameOrderEvenWhenTheDirectoryOrderChanges() {
        var candidates = new ArrayList<>(List.of(candidate(3, 101, AvailabilityStatus.AVAILABLE),
                candidate(1, 103, AvailabilityStatus.AVAILABLE), candidate(2, 102, AvailabilityStatus.AVAILABLE)));
        when(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 5L)).thenReturn(candidates);
        for (var candidate : candidates) when(workload.countOpenWorkOrders(candidate.userId())).thenReturn(2L);
        var first = recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L);
        Collections.reverse(candidates);
        var second = recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L);
        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(TechnicianRecommendationResponse::profileId).containsExactly(1L, 2L, 3L);
        assertThat(candidates).extracting(TechnicianCandidateResponse::profileId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void comparesLongCountsWithoutNarrowingOrSubtractingThem() {
        when(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 5L)).thenReturn(List.of(
                candidate(1, 101, AvailabilityStatus.BUSY), candidate(2, 102, AvailabilityStatus.BUSY)));
        when(workload.countOpenWorkOrders(101L)).thenReturn(Long.MAX_VALUE);
        when(workload.countOpenWorkOrders(102L)).thenReturn(0L);
        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .extracting(TechnicianRecommendationResponse::userId).containsExactly(102L, 101L);
    }

    @Test
    void emptyCandidatesDoNotRequireAWorkloadLookup() {
        when(directory.findCandidates(MaintenanceCategory.PLUMBING, 5L)).thenReturn(List.of());
        assertThat(recommendations.recommend(MaintenanceCategory.PLUMBING, 5L)).isEmpty();
        verifyNoInteractions(workload);
    }

    @Test
    void preservesDirectoryValidationAndDoesNotQueryWorkloadOnInvalidInput() {
        when(directory.findCandidates(null, 5L)).thenThrow(new InputValidationException("Category is required"));
        assertThatThrownBy(() -> recommendations.recommend(null, 5L)).isInstanceOf(InputValidationException.class);
        verifyNoInteractions(workload);
    }

    @Test
    void workloadFailureRejectsTheRankingRatherThanReturningAPartialResult() {
        when(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 5L)).thenReturn(List.of(
                candidate(1, 101, AvailabilityStatus.AVAILABLE), candidate(2, 102, AvailabilityStatus.AVAILABLE)));
        when(workload.countOpenWorkOrders(101L)).thenReturn(4L);
        when(workload.countOpenWorkOrders(102L)).thenThrow(new BusinessConflictException("Lookup unavailable"));
        assertThatThrownBy(() -> recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .isInstanceOf(BusinessConflictException.class);
    }

    private static TechnicianCandidateResponse candidate(long profileId, long userId, AvailabilityStatus availability) {
        return new TechnicianCandidateResponse(profileId, userId, "Technician " + userId,
                Set.of(MaintenanceCategory.ELECTRICAL), Set.of(5L), availability);
    }
}
