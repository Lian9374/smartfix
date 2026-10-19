package com.smartfix.notification.service;
import com.smartfix.notification.config.DeliveryProperties;
import com.smartfix.notification.event.DeliveryQueuedEvent;
import com.smartfix.notification.repository.DeliveryTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import java.time.Clock;

@Service
public class DeliveryDispatcher {
    @org.springframework.beans.factory.annotation.Value("${smartfix.cloud.enabled:false}")
    private boolean distributed;
    private final DeliveryTaskRepository tasks;private final DeliveryWorker worker;
    private final Clock clock;private final DeliveryProperties properties;
    public DeliveryDispatcher(DeliveryTaskRepository tasks,DeliveryWorker worker,Clock clock,DeliveryProperties properties){this.tasks=tasks;this.worker=worker;this.clock=clock;this.properties=properties;}
    public void drain(){if(distributed)return;for(Long id:tasks.due(clock.instant(),PageRequest.of(0,properties.batchSize)))try{worker.deliver(id);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Delivery scan failed: taskId={}, failureType={}",id,failure.getClass().getSimpleName());}}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void committed(DeliveryQueuedEvent event){if(properties.eager)try{drain();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Delivery wakeup failed: failureType={}",failure.getClass().getSimpleName());}}
}
