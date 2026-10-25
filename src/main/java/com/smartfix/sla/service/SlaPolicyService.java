package com.smartfix.sla.service;
import com.smartfix.audit.service.AuditService;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.request.domain.UrgencyLevel;
import com.smartfix.sla.domain.*;
import com.smartfix.sla.repository.*;
import com.smartfix.user.service.NotificationContactService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
@Service
public class SlaPolicyService {
    private final SlaPolicyRepository policies;private final SlaStateRepository states;
    private final NotificationContactService access;private final AuditService audit;private final Clock clock;
    public SlaPolicyService(SlaPolicyRepository policies,SlaStateRepository states,NotificationContactService access,AuditService audit,Clock clock){this.policies=policies;this.states=states;this.access=access;this.audit=audit;this.clock=clock;}
    @Transactional public void configure(Long actor,UrgencyLevel urgency,SlaPhase phase,long minutes,long reminderMinutes,boolean enabled){
        access.requireAdmin(actor);if(urgency==null||phase==null||minutes<1||minutes>525600||reminderMinutes<0||reminderMinutes>minutes)throw new InputValidationException("Choose a duration from 1 to 525600 minutes and a reminder between zero and the duration.");
        var policy=policies.findByUrgencyAndPhase(urgency,phase).orElseGet(()->new SlaPolicy(urgency,phase));
        policy.configure(minutes*60,reminderMinutes*60,enabled);policies.saveAndFlush(policy);
        audit.record(actor,"SLA_POLICY_UPDATED","SLA_POLICY",policy.getId(),enabled?"ENABLED":"DISABLED",clock.instant());
    }
    @Transactional(readOnly=true) public List<SlaPolicy> policies(Long actor){access.requireAdmin(actor);return policies.findAll(Sort.by("urgency","phase"));}
    @Transactional(readOnly=true) public Page<SlaState.Snapshot> states(Long actor,int page){access.requireAdmin(actor);return states.findAllByOrderByDueAtAscIdAsc(PageRequest.of(Math.max(0,page),20)).map(SlaState::snapshot);}
    /** D read contract. Empty explicitly means UNCONFIGURED; never fabricate a deadline. */
    @Transactional(readOnly=true) public ReadResult read(Long actor,Long requestId){access.requireAdmin(actor);var rows=states.findByRequestIdOrderByPhaseAsc(requestId).stream().map(SlaState::snapshot).toList();return new ReadResult(rows.isEmpty()?"UNCONFIGURED":"AVAILABLE",rows);}
    public record ReadResult(String availability,List<SlaState.Snapshot> phases){
    // Own the collection structure; preserve existing null/element semantics.
    public ReadResult {
        phases = phases == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(phases));
    }
}
}
