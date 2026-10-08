package com.smartfix.request.service;

import com.smartfix.request.spi.ActiveAssignmentLookup;
import com.smartfix.request.spi.ActiveAssignmentLookup.ActiveAssignment;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestAssignmentAccessServiceTest {
    @Mock ObjectProvider<ActiveAssignmentLookup> adapters;
    @Mock ActiveAssignmentLookup adapter;
    @Mock UserService users;

    @Test
    void missingAdapterGrantsNeitherDetailNorListAccess() {
        var access = service();
        assertThat(access.isAssignedTo(1L, 101L)).isFalse();
        assertThat(access.findActiveRequestIdsForTechnician(101L)).isEmpty();
        assertThat(access.isAvailable()).isFalse();
        verifyNoInteractions(users);
    }

    @Test
    void assignmentForAnotherRequestCannotAuthorizeThisRequest() {
        when(adapters.getIfAvailable()).thenReturn(adapter);
        when(adapter.findActiveAssignment(1L)).thenReturn(Optional.of(new ActiveAssignment(10L, 999L, 101L)));
        assertThat(service().isAssignedTo(1L, 101L)).isFalse();
    }

    @Test
    void onlyTheMatchingTechnicianAccountCanUseAnAssignment() {
        when(adapters.getIfAvailable()).thenReturn(adapter);
        when(adapter.findActiveAssignment(1L)).thenReturn(Optional.of(new ActiveAssignment(10L, 1L, 101L)));
        assertThat(service().isAssignedTo(1L, 101L)).isTrue();
        assertThat(service().isAssignedTo(1L, 102L)).isFalse();
    }

    @Test
    void olderAdapterWithoutListSupportCannotExposeHistoricalWorkOrders() {
        ActiveAssignmentLookup older = request -> Optional.of(new ActiveAssignment(10L, request, 101L));
        when(adapters.getIfAvailable()).thenReturn(older);
        assertThat(service().isAssignedTo(1L, 101L)).isTrue();
        assertThat(service().findActiveRequestIdsForTechnician(101L)).isEmpty();
    }

    @Test
    void currentIdsAreAnImmutableSnapshotAndAdapterFailurePropagates() {
        when(adapters.getIfAvailable()).thenReturn(adapter);
        Set<Long> ids = new HashSet<>(Set.of(1L, 2L));
        when(adapter.findActiveRequestIdsForTechnician(101L)).thenReturn(ids);
        var result = service().findActiveRequestIdsForTechnician(101L);
        ids.clear();
        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
        assertThatThrownBy(() -> result.add(3L)).isInstanceOf(UnsupportedOperationException.class);
        when(adapter.findActiveRequestIdsForTechnician(101L)).thenThrow(new IllegalStateException("Unavailable"));
        assertThatThrownBy(() -> service().findActiveRequestIdsForTechnician(101L)).isInstanceOf(IllegalStateException.class);
    }

    private RequestAssignmentAccessService service() { return new RequestAssignmentAccessService(adapters, users); }
}
