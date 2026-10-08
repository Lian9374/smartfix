package com.smartfix.technician.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.request.service.RequestAssignmentAccessService;
import com.smartfix.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicianWorkloadServiceTest {
    @Mock WorkOrderService workOrders;
    @Mock RequestAssignmentAccessService assignments;
    @InjectMocks TechnicianWorkloadService workload;

    @ParameterizedTest
    @ValueSource(longs = {0, 5, 2147483648L})
    void returnsTheOwningServicesRealCountUsingTheAccountId(long count) {
        when(assignments.isAvailable()).thenReturn(true);
        when(workOrders.countOpenWorkOrders(501L)).thenReturn(count);
        assertThat(workload.countOpenWorkOrders(501L)).isEqualTo(count);
        verify(workOrders).countOpenWorkOrders(501L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidAccountIdsBeforeQueryingAnyModule(Long userId) {
        assertThatThrownBy(() -> workload.countOpenWorkOrders(userId)).isInstanceOf(InputValidationException.class);
        verifyNoInteractions(workOrders, assignments);
    }

    @Test
    void missingAssignmentLookupIsNotReportedAsZeroWorkload() {
        when(assignments.isAvailable()).thenReturn(false);
        assertThatThrownBy(() -> workload.countOpenWorkOrders(501L))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("assignment lookup");
        verifyNoInteractions(workOrders);
    }

    @Test
    void databaseFailurePropagatesInsteadOfMakingABusyTechnicianLookIdle() {
        when(assignments.isAvailable()).thenReturn(true);
        var failure = new DataAccessResourceFailureException("test-only database outage");
        when(workOrders.countOpenWorkOrders(501L)).thenThrow(failure);
        assertThatThrownBy(() -> workload.countOpenWorkOrders(501L)).isSameAs(failure);
    }
}
