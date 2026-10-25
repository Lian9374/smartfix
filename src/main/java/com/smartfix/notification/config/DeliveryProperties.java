package com.smartfix.notification.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class DeliveryProperties {
    public final int maxAttempts,batchSize;
    public final long retrySeconds;
    public final boolean eager,emailEnabled;
    public DeliveryProperties(@Value("${smartfix.notification.delivery.max-attempts:4}") int attempts,
            @Value("${smartfix.notification.delivery.batch-size:50}") int batch,
            @Value("${smartfix.notification.delivery.retry-seconds:1}") long retry,
            @Value("${smartfix.notification.delivery.eager-enabled:true}") boolean eager,
            @Value("${smartfix.notification.email.enabled:false}") boolean email) {
        if(attempts<1||attempts>20||batch<1||batch>200||retry<1||retry>3600)throw new IllegalArgumentException("Invalid delivery configuration");
        this.maxAttempts=attempts;this.batchSize=batch;this.retrySeconds=retry;this.eager=eager;this.emailEnabled=email;
    }
    public long backoff(int attempts){return Math.min(3600,retrySeconds*(attempts<=1?1:attempts==2?5:30*(1L<<Math.min(10,attempts-3))));}
}
