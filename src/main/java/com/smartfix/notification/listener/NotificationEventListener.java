package com.smartfix.notification.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.smartfix.notification.service.NotificationService;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.workorder.event.WorkOrderCompletedEvent;

@Component
public class NotificationEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void on(RequestStatusChangedEvent event) {

        try {
            notificationService.createNotification(
                    event.requesterId(),
                    "REQUEST_STATUS_CHANGED",
                    "Maintenance Request Updated",
                    "Request " + event.ticketNumber()
                            + " status changed to "
                            + event.toStatus().name() + ".",
                    event.requestId()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to process request status notification",
                    exception.getClass().getSimpleName()
            );
        }
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void on(WorkOrderCompletedEvent event) {

        try {
            notificationService.createNotification(
                    event.requesterId(),
                    "WORK_ORDER_COMPLETED",
                    "Maintenance Work Completed",
                    "Maintenance work for request "
                            + event.ticketNumber()
                            + " has been completed.",
                    event.requestId()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to process work order notification",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
