package com.smartfix.notification.listener;
import com.smartfix.sla.event.*;
import com.smartfix.notification.service.DeliveryOutboxService;
import com.smartfix.user.service.NotificationContactService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class SlaNotificationListener{
    private final NotificationContactService users;private final DeliveryOutboxService outbox;
    public SlaNotificationListener(NotificationContactService users,DeliveryOutboxService outbox){this.users=users;this.outbox=outbox;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void breached(SlaBreachedEvent e){send(e.stateId(),e.requestId(),e.cycle(),"SLA_BREACHED","Maintenance SLA overdue");}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void reminder(SlaDueSoonEvent e){send(e.stateId(),e.requestId(),e.cycle(),"SLA_DUE_SOON","Maintenance SLA due soon");}
    private void send(Long state,Long request,int cycle,String type,String title){for(Long admin:users.activeAdministratorIds())outbox.enqueue(admin,type,title,"Review this maintenance request in the administrator SLA workspace.",request,type+":"+state+":"+cycle);}
}
