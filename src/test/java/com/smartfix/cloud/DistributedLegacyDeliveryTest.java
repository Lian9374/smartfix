package com.smartfix.cloud;

import com.smartfix.notification.service.*;
import com.smartfix.notification.repository.DeliveryTaskRepository;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.notification.config.DeliveryProperties;
import com.smartfix.user.service.NotificationContactService;
import com.smartfix.audit.service.AuditService;
import com.smartfix.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistributedLegacyDeliveryTest {
    @Test void skippedWorkerReceiptCannotBeReusedByLegacyRetry() {
        var tasks=mock(DeliveryTaskRepository.class);
        var task=new DeliveryTask(7L,"EMAIL","STATUS","Updated","Resolved",1L,"event:7",Instant.now());
        task.skipped();
        when(tasks.locked(1L)).thenReturn(Optional.of(task));
        var service=new DeliveryAdministrationService(tasks,mock(NotificationContactService.class),mock(AuditService.class),Clock.systemUTC());
        ReflectionTestUtils.setField(service,"distributed",true);
        assertThrows(BusinessConflictException.class,()->service.retry(9L,1L));
        assertEquals("SKIPPED",task.getStatus());
    }
    @Test void legacyDrainCannotExecuteCloudTasksInCore() {
        var tasks=mock(DeliveryTaskRepository.class);
        when(tasks.due(any(),any())).thenThrow(new AssertionError("Core must leave delivery to SQS Worker"));
        var dispatcher=new DeliveryDispatcher(tasks,mock(DeliveryWorker.class),Clock.systemUTC(),new DeliveryProperties(4,50,1,false,false));
        ReflectionTestUtils.setField(dispatcher,"distributed",true);
        assertDoesNotThrow(dispatcher::drain);
    }
}
