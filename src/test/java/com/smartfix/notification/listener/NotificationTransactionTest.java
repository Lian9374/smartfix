package com.smartfix.notification.listener;

import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartfix.notification.service.NotificationService;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.event.RequestStatusChangedEvent;

class NotificationTransactionTest {

    private AnnotationConfigApplicationContext context;

    private NotificationService notificationService;

    private static final Instant NOW =
            Instant.parse("2026-10-08T10:00:00Z");

    @BeforeEach
    void setUp() {

        notificationService =
                mock(NotificationService.class);

        context =
                new AnnotationConfigApplicationContext();

        context.registerBean(
                NotificationService.class,
                () -> notificationService
        );

        context.register(TestConfiguration.class);

        context.register(NotificationEventListener.class);

        context.refresh();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * T7: Rolling back the business transaction
     * must not generate any notification.
     */
    @Test
    void rollbackDoesNotCreateNotification() {

        TransactionTemplate transaction =
                new TransactionTemplate(
                        context.getBean(
                                DataSourceTransactionManager.class
                        )
                );

        transaction.executeWithoutResult(status -> {

            context.publishEvent(createEvent());

            status.setRollbackOnly();
        });

        verifyNoInteractions(notificationService);
    }

    /**
     * A successfully committed business transaction
     * must trigger the notification listener.
     */
    @Test
    void committedTransactionCreatesNotification() {

        TransactionTemplate transaction =
                new TransactionTemplate(
                        context.getBean(
                                DataSourceTransactionManager.class
                        )
                );

        transaction.executeWithoutResult(status -> {

            context.publishEvent(createEvent());

        });

        verify(notificationService, times(1))
                .createNotification(
                        eq(10L),
                        eq("REQUEST_STATUS_CHANGED"),
                        eq("Maintenance Request Updated"),
                        contains("SF-2026-000001"),
                        eq(100L),
                        startsWith("REQUEST_STATUS_CHANGED:100:")
                );
    }

    private RequestStatusChangedEvent createEvent() {

        return new RequestStatusChangedEvent(
                100L,
                "SF-2026-000001",
                10L,
                RequestStatus.SUBMITTED,
                RequestStatus.UNDER_REVIEW,
                20L,
                NOW
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {

            return new DriverManagerDataSource(
                    "jdbc:h2:mem:notification_transaction_test;"
                            + "DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        DataSourceTransactionManager transactionManager(
                DataSource dataSource
        ) {
            return new DataSourceTransactionManager(
                    dataSource
            );
        }
    }
}