package com.smartfix.notification.listener;
import com.smartfix.community.event.*;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.notification.service.DeliveryOutboxService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@org.springframework.context.annotation.Profile("!community-remote")
@Component
public class CommunityNotificationListener {
    private final DeliveryOutboxService outbox;
    public CommunityNotificationListener(DeliveryOutboxService outbox){this.outbox=outbox;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void answered(CommunityAnswerCreatedEvent e){
        if(e.questionAuthorId().equals(e.answerAuthorId()))return;
        outbox.enqueue(e.questionAuthorId(),"COMMUNITY_ANSWER_CREATED","New answer to your question",
            "Someone answered your campus question. Open the discussion to read their reply.",e.questionId(),
            "COMMUNITY_ANSWER_CREATED:"+e.answerId());
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void accepted(CommunityAnswerAcceptedEvent e){
        if(e.questionAuthorId().equals(e.answerAuthorId()))return;
        outbox.enqueue(e.answerAuthorId(),"COMMUNITY_ANSWER_ACCEPTED","Your answer was accepted",
            "The person who asked the question accepted your answer.",e.questionId(),
            "COMMUNITY_ANSWER_ACCEPTED:"+e.answerId()+":"+e.occurredAt());
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void hidden(CommunityContentHiddenEvent e){
        if(e.resultingStatus()!=CommunityContentStatus.HIDDEN)return;
        outbox.enqueue(e.authorId(),"COMMUNITY_CONTENT_HIDDEN","Community content hidden",
            "A moderator hid your "+e.contentType().name().toLowerCase(java.util.Locale.ROOT)+". It is no longer publicly visible.",null,
            "COMMUNITY_CONTENT_HIDDEN:"+e.contentType()+":"+e.contentId()+":"+e.occurredAt());
    }
}
