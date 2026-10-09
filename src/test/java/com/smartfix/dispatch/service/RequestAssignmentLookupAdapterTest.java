package com.smartfix.dispatch.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestAssignmentLookupAdapterTest {
    private final AssignmentReadService assignments = mock(AssignmentReadService.class);
    private final RequestAssignmentLookupAdapter adapter = new RequestAssignmentLookupAdapter(assignments);

    @Test
    void bulkReadRestrictsTheResultToCurrentTechnicianAndCandidateRequests() {
        when(assignments.findActiveRequestIdsForTechnician(101L)).thenReturn(Set.of(1L, 3L));

        var result = adapter.findActiveRequestIds(101L, List.of(1L, 1L, 2L));

        assertThat(result).containsExactly(1L);
        assertThatThrownBy(() -> result.add(3L)).isInstanceOf(UnsupportedOperationException.class);
        verify(assignments).findActiveRequestIdsForTechnician(101L);
        verifyNoMoreInteractions(assignments);
    }

    @Test
    void emptyCandidatesDoNotReadAssignments() {
        assertThat(adapter.findActiveRequestIds(101L, Set.of())).isEmpty();
        verifyNoInteractions(assignments);
    }
}
