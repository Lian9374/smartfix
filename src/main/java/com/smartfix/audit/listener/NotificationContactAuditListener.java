package com.smartfix.audit.listener;
import com.smartfix.audit.service.AuditService;
import com.smartfix.user.event.NotificationContactChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class NotificationContactAuditListener {
    private final AuditService audit;
    public NotificationContactAuditListener(AuditService audit){this.audit=audit;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void changed(NotificationContactChangedEvent event){audit.record(event.actorId(),"NOTIFICATION_CONTACT_UPDATED","USER",event.userId(),event.configured()?"SET":"CLEARED",event.occurredAt());}
}
