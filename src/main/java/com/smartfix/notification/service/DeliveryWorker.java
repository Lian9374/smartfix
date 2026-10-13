package com.smartfix.notification.service;
import com.smartfix.notification.config.DeliveryProperties;
import com.smartfix.notification.repository.DeliveryTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;

@Service
public class DeliveryWorker {
    private final DeliveryTaskRepository tasks;private final NotificationService notifications;
    private final EmailDeliveryService email;private final Clock clock;private final DeliveryProperties properties;
    private final com.smartfix.audit.service.AuditService audit;
    public DeliveryWorker(DeliveryTaskRepository tasks,NotificationService notifications,EmailDeliveryService email,Clock clock,DeliveryProperties properties,com.smartfix.audit.service.AuditService audit){this.tasks=tasks;this.notifications=notifications;this.email=email;this.clock=clock;this.properties=properties;this.audit=audit;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void deliver(Long id){
        var task=tasks.locked(id).orElse(null);
        if(task==null||!task.ready(clock.instant()))return;
        task.attempted();
        try{
            if(task.getChannel().equals("EMAIL")){
                if(!properties.emailEnabled)throw new IllegalStateException("Email delivery disabled");
                if(!email.send(task)){task.skipped();return;}
            }else notifications.createNotification(task.getRecipientId(),task.getEventType(),task.getTitle(),task.getMessage(),task.getReferenceId(),task.getDedupKey());
            task.delivered(clock.instant());
        }catch(RuntimeException failure){
            task.failed(failure.getClass().getSimpleName(),properties.maxAttempts,clock.instant().plusSeconds(properties.backoff(task.getAttempts())));
            if(task.getStatus().equals("FAILED"))audit.recordDeliveryExhausted(id,clock.instant());
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Delivery failed: taskId={}, channel={}, attempt={}, failureType={}",id,task.getChannel(),task.getAttempts(),failure.getClass().getSimpleName());
        }
    }
}
