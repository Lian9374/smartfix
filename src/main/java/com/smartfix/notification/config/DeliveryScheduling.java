package com.smartfix.notification.config;
import com.smartfix.notification.service.DeliveryDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling
@ConditionalOnProperty(name="smartfix.notification.delivery.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class DeliveryScheduling {
    private final DeliveryDispatcher dispatcher;
    public DeliveryScheduling(DeliveryDispatcher dispatcher){this.dispatcher=dispatcher;}
    @Scheduled(fixedDelayString="${smartfix.notification.delivery.scan-milliseconds:15000}")
    public void delivery(){dispatcher.drain();}
    @org.springframework.beans.factory.annotation.Autowired
    private com.smartfix.sla.service.SlaScanner sla;
    @Scheduled(fixedDelayString="${smartfix.sla.scan-milliseconds:60000}")
    public void sla(){sla.scan();}
}
