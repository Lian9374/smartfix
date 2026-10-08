package com.smartfix.notification.listener;

import com.smartfix.community.event.*;
import com.smartfix.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class CommunityNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(CommunityNotificationListener.class);
    private final NotificationService notifications;
    public CommunityNotificationListener(NotificationService notifications) { this.notifications = notifications; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void answered(CommunityAnswerCreatedEvent event) {
        if (event.questionAuthorId().equals(event.answerAuthorId())) { return; }
        deliver(event.questionAuthorId(), "COMMUNITY_ANSWER_CREATED", "New answer to your question",
                "Someone answered your campus question. Open the discussion to read their reply.", event.questionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void accepted(CommunityAnswerAcceptedEvent event) {
        if (event.questionAuthorId().equals(event.answerAuthorId())) { return; }
        deliver(event.answerAuthorId(), "COMMUNITY_ANSWER_ACCEPTED", "Your answer was accepted",
                "The person who asked the question accepted your answer.", event.questionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void hidden(CommunityContentHiddenEvent event) {
        if (event.resultingStatus() != com.smartfix.community.domain.CommunityContentStatus.HIDDEN) { return; }
        deliver(event.authorId(), "COMMUNITY_CONTENT_HIDDEN", "Community content hidden",
                "A moderator hid your " + event.contentType().name().toLowerCase(java.util.Locale.ROOT)
                        + ". It is no longer publicly visible.", null);
    }

    private void deliver(Long recipient, String type, String title, String message, Long reference) {
        try {
            notifications.createNotification(recipient, type, title, message, reference);
        } catch (RuntimeException failure) {
            // Business data is already committed. Record failure without exposing content or credentials.
            log.error("Community notification delivery failed: eventType={}, referenceId={}, failureType={}",
                    type, reference, failure.getClass().getSimpleName());
        }
    }
}
