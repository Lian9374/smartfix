package com.smartfix.notification.service;
import com.smartfix.notification.config.DeliveryProperties;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.notification.event.DeliveryQueuedEvent;
import com.smartfix.notification.repository.DeliveryTaskRepository;
import com.smartfix.notification.repository.DeliveryTaskInsertRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;

@Service
public class DeliveryOutboxService {
    private final DeliveryTaskInsertRepository insert;private final Clock clock;
    private final DeliveryProperties properties;private final ApplicationEventPublisher events;
    public DeliveryOutboxService(DeliveryTaskInsertRepository insert,Clock clock,DeliveryProperties properties,ApplicationEventPublisher events){this.insert=insert;this.clock=clock;this.properties=properties;this.events=events;}
    /** Stage inside the business transaction; only external delivery happens AFTER_COMMIT. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(Long recipient,String type,String title,String message,Long reference,String eventKey){
        stage(recipient,"IN_APP",type,title,message,reference,eventKey+":"+recipient+":IN_APP");
        if(properties.emailEnabled)stage(recipient,"EMAIL",type,title,message,reference,eventKey+":"+recipient+":EMAIL");
        events.publishEvent(new DeliveryQueuedEvent());
    }
    private void stage(Long recipient,String channel,String type,String title,String message,Long reference,String key){
        new DeliveryTask(recipient,channel,type,title,message,reference,key,clock.instant());
        insert.insert(recipient,channel,type,title,message,reference,key,clock.instant());
    }
}
