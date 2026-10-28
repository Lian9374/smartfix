package com.smartfix.notification.listener;
import com.smartfix.notification.service.DeliveryOutboxService;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.workorder.event.WorkOrderCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class NotificationEventListener {
    private final DeliveryOutboxService outbox;
    public NotificationEventListener(DeliveryOutboxService outbox){this.outbox=outbox;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void on(RequestStatusChangedEvent e){
        String identity=e.historyId()!=null?"HISTORY:"+e.historyId():
            (e.fromStatus()==null?"INITIAL":e.fromStatus().name())+":"+e.toStatus()+":"+e.occurredAt();
        outbox.enqueue(e.requesterId(),"REQUEST_STATUS_CHANGED","Maintenance Request Updated",
            "Request "+e.ticketNumber()+" status changed to "+e.toStatus()+".",e.requestId(),
            "REQUEST_STATUS_CHANGED:"+e.requestId()+":"+identity);
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void on(WorkOrderCompletedEvent e){
        String identity=e.resolutionHistoryId()!=null?"HISTORY:"+e.resolutionHistoryId():e.occurredAt().toString();
        outbox.enqueue(e.requesterId(),"WORK_ORDER_COMPLETED","Maintenance Work Completed",
            "Maintenance work for request "+e.ticketNumber()+" has been completed.",e.requestId(),
            "WORK_ORDER_COMPLETED:"+e.workOrderId()+":"+identity);
    }
}
