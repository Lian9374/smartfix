package com.smartfix.communitybridge;

import com.smartfix.notification.service.DeliveryWorker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import java.util.List;

/** Core-owned local acceptance configuration: deliver repair notices, never scan mail or SLA. */
@Configuration @Profile("community-remote") @EnableScheduling
@ConditionalOnProperty(name="smartfix.migration.scenario",havingValue="stage8")
public class Stage8InAppDelivery {
    private final JdbcTemplate db;private final DeliveryWorker worker;
    public Stage8InAppDelivery(JdbcTemplate db,DeliveryWorker worker){this.db=db;this.worker=worker;}
    @Scheduled(fixedDelay=1000) public void drain(){
        List<Long> ids=db.queryForList("SELECT id FROM delivery_tasks WHERE channel='IN_APP' AND status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY next_attempt_at,id LIMIT 20",Long.class);
        for(Long id:ids)try{worker.deliver(id);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("In-app acceptance delivery failed: task={}, type={}",id,e.getClass().getSimpleName());}
    }
}
