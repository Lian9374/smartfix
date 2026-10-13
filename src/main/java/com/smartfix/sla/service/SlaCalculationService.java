package com.smartfix.sla.service;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.service.RequestReadService;
import com.smartfix.sla.domain.*;
import com.smartfix.sla.repository.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import java.util.*;

@Service
public class SlaCalculationService {
    private final SlaStateRepository states;private final SlaPolicyRepository policies;private final RequestReadService requests;
    public SlaCalculationService(SlaStateRepository states,SlaPolicyRepository policies,RequestReadService requests){this.states=states;this.policies=policies;this.requests=requests;}
    @Order(-100) @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void changed(RequestStatusChangedEvent event){
        if(!policies.existsByEnabledTrue()&&!states.existsByRequestId(event.requestId()))return;
        var request=requests.findById(event.requestId());
        Map<SlaPhase,SlaPolicy> configured=new EnumMap<>(SlaPhase.class);
        policies.findByEnabledTrueAndUrgency(request.effectiveUrgencyLevel()).forEach(p->configured.put(p.getPhase(),p));
        Map<SlaPhase,SlaState> current=new EnumMap<>(SlaPhase.class);
        states.lockedForRequest(event.requestId()).forEach(s->current.put(s.getPhase(),s));
        String key=event.historyId()!=null?"HISTORY:"+event.historyId():event.toStatus()+":"+event.occurredAt();
        if(!current.isEmpty()&&current.values().stream().allMatch(s->s.processed(key)))return;
        boolean hadResolution=current.containsKey(SlaPhase.RESOLUTION);
        for(SlaPhase phase:List.of(SlaPhase.RECEIPT,SlaPhase.ASSIGNMENT,SlaPhase.RESOLUTION)){
            if(!current.containsKey(phase)&&configured.containsKey(phase)){
                var state=states.save(new SlaState(event.requestId(),configured.get(phase),phase==SlaPhase.RESOLUTION&&event.toStatus()==RequestStatus.REOPENED?event.occurredAt():request.createdAt()));current.put(phase,state);
            }
        }
        var target=event.toStatus();
        if(target!=RequestStatus.SUBMITTED)complete(current,SlaPhase.RECEIPT);
        if(target==RequestStatus.ASSIGNED||target==RequestStatus.IN_PROGRESS||target==RequestStatus.RESOLVED||target==RequestStatus.CONFIRMED||target==RequestStatus.CLOSED)complete(current,SlaPhase.ASSIGNMENT);
        var resolution=current.get(SlaPhase.RESOLUTION);
        if(target==RequestStatus.UNDER_REVIEW&&(event.fromStatus()==RequestStatus.ASSIGNED||event.fromStatus()==RequestStatus.IN_PROGRESS)&&resolution!=null)resolution.pause(event.occurredAt());
        if((target==RequestStatus.ASSIGNED||target==RequestStatus.IN_PROGRESS)&&resolution!=null)resolution.resume(event.occurredAt());
        if(target==RequestStatus.REOPENED){
            complete(current,SlaPhase.CONFIRMATION);
            if(hadResolution&&resolution!=null&&configured.containsKey(SlaPhase.RESOLUTION))resolution.restart(configured.get(SlaPhase.RESOLUTION),event.occurredAt());
        }
        if(target==RequestStatus.RESOLVED){
            complete(current,SlaPhase.RESOLUTION);
            if(!current.containsKey(SlaPhase.CONFIRMATION)&&configured.containsKey(SlaPhase.CONFIRMATION))
                current.put(SlaPhase.CONFIRMATION,states.save(new SlaState(event.requestId(),configured.get(SlaPhase.CONFIRMATION),request.createdAt())));
            else if(current.containsKey(SlaPhase.CONFIRMATION)&&configured.containsKey(SlaPhase.CONFIRMATION))
                current.get(SlaPhase.CONFIRMATION).restart(configured.get(SlaPhase.CONFIRMATION),request.createdAt());
        }
        if(target==RequestStatus.CONFIRMED)complete(current,SlaPhase.CONFIRMATION);
        if(target==RequestStatus.CLOSED||target==RequestStatus.REJECTED||target==RequestStatus.CANCELLED)current.values().forEach(SlaState::complete);
        current.values().forEach(s->s.processedEvent(key));
    }
    private void complete(Map<SlaPhase,SlaState> states,SlaPhase phase){var state=states.get(phase);if(state!=null)state.complete();}
}
