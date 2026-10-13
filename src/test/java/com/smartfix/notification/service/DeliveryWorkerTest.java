package com.smartfix.notification.service;
import com.smartfix.notification.config.DeliveryProperties;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.notification.repository.DeliveryTaskRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeliveryWorkerTest {
    private final Instant now=Instant.parse("2026-10-13T00:00:00Z");
    @Test void failedInsertIsRetriedWithBackoffAndEventuallyFailed(){var tasks=mock(DeliveryTaskRepository.class);var notifications=mock(NotificationService.class);var task=new DeliveryTask(1L,"IN_APP","TEST","Test","Generic test",null,"stable",now);when(tasks.locked(1L)).thenReturn(Optional.of(task));doThrow(new IllegalStateException()).when(notifications).createNotification(any(),any(),any(),any(),any(),any());var props=new DeliveryProperties(2,10,30,false,false);var worker=new DeliveryWorker(tasks,notifications,mock(EmailDeliveryService.class),Clock.fixed(now,ZoneOffset.UTC),props,mock(com.smartfix.audit.service.AuditService.class));assertDoesNotThrow(()->worker.deliver(1L));assertEquals("PENDING",task.getStatus());assertEquals(now.plusSeconds(30),task.getNextAttemptAt());worker.deliver(1L);assertEquals(1,task.getAttempts());new DeliveryWorker(tasks,notifications,mock(EmailDeliveryService.class),Clock.fixed(now.plusSeconds(30),ZoneOffset.UTC),props,mock(com.smartfix.audit.service.AuditService.class)).deliver(1L);assertEquals("FAILED",task.getStatus());assertEquals(2,task.getAttempts());}
    @Test void deliveredTaskDoesNotSendAgain(){var tasks=mock(DeliveryTaskRepository.class);var notifications=mock(NotificationService.class);var task=new DeliveryTask(1L,"IN_APP","TEST","Test","Generic test",null,"stable",now);when(tasks.locked(1L)).thenReturn(Optional.of(task));var worker=new DeliveryWorker(tasks,notifications,mock(EmailDeliveryService.class),Clock.fixed(now,ZoneOffset.UTC),new DeliveryProperties(2,10,30,false,false),mock(com.smartfix.audit.service.AuditService.class));worker.deliver(1L);worker.deliver(1L);assertEquals("DELIVERED",task.getStatus());verify(notifications,times(1)).createNotification(any(),any(),any(),any(),any(),eq("stable"));}
    @Test void missingContactDoesNotPretendEmailWasSent(){var tasks=mock(DeliveryTaskRepository.class);var email=mock(EmailDeliveryService.class);var task=new DeliveryTask(1L,"EMAIL","TEST","Test","Generic test",null,"stable",now);when(tasks.locked(1L)).thenReturn(Optional.of(task));new DeliveryWorker(tasks,mock(NotificationService.class),email,Clock.fixed(now,ZoneOffset.UTC),new DeliveryProperties(2,10,30,false,true),mock(com.smartfix.audit.service.AuditService.class)).deliver(1L);assertEquals("SKIPPED",task.getStatus());}
    @Test void backoffAndConfigurationAreBounded(){var props=new DeliveryProperties(5,50,30,true,false);assertEquals(30,props.backoff(1));assertEquals(150,props.backoff(2));assertEquals(3600,props.backoff(20));assertThrows(IllegalArgumentException.class,()->new DeliveryProperties(0,50,30,true,false));}
}
