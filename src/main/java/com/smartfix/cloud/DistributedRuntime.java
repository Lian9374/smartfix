package com.smartfix.cloud;

import com.smartfix.sla.service.SlaScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import java.net.URI;
import java.time.Duration;

@Configuration
@Profile("distributed")
@EnableScheduling
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds=1800)
public class DistributedRuntime {
    private final SlaScanner sla;
    public DistributedRuntime(SlaScanner sla) { this.sla = sla; }
    @Bean TransactionTemplate cloudTransactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean SqsClient sqs(@Value("${smartfix.cloud.region:ap-southeast-1}") String region,
                       @Value("${smartfix.cloud.sqs-endpoint:}") String endpoint) {
        var builder = SqsClient.builder().region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(8)).apiCallAttemptTimeout(Duration.ofSeconds(6)));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }
    @Scheduled(fixedDelayString="${smartfix.sla.scan-milliseconds:60000}")
    public void scanSla() { sla.scan(); }
}
