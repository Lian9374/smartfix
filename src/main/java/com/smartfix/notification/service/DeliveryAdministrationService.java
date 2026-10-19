package com.smartfix.notification.service;
import com.smartfix.audit.service.AuditService;
import com.smartfix.common.exception.*;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.notification.repository.DeliveryTaskRepository;
import com.smartfix.user.service.NotificationContactService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Service
public class DeliveryAdministrationService{
    @org.springframework.beans.factory.annotation.Value("${smartfix.cloud.enabled:false}")
    private boolean distributed;
    private final DeliveryTaskRepository tasks;private final NotificationContactService access;private final AuditService audit;private final Clock clock;
    public DeliveryAdministrationService(DeliveryTaskRepository tasks,NotificationContactService access,AuditService audit,Clock clock){this.tasks=tasks;this.access=access;this.audit=audit;this.clock=clock;}
    @Transactional(readOnly=true) public Page<DeliveryTask.Summary> list(Long actor,int page){access.requireAdmin(actor);return tasks.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(0,page),20)).map(DeliveryTask::summary);}
    @Transactional public void retry(Long actor,Long id){access.requireAdmin(actor);var task=tasks.locked(id).orElseThrow(()->new ResourceNotFoundException("Delivery task not found"));if(distributed && "SKIPPED".equals(task.getStatus()))throw new BusinessConflictException("Skipped cloud email receipts cannot be retried; create a new delivery after correcting the contact.");if(!java.util.Set.of("FAILED","SKIPPED").contains(task.getStatus()))throw new BusinessConflictException("Only failed or skipped tasks may be retried.");task.retry(clock.instant());audit.record(actor,"DELIVERY_RETRY_REQUESTED","DELIVERY",id,"PENDING",clock.instant());}
}
