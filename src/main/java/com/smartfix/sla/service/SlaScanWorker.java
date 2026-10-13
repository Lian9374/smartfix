package com.smartfix.sla.service;
import com.smartfix.sla.repository.SlaStateRepository;
import com.smartfix.sla.event.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
@Service
public class SlaScanWorker{
    private final SlaStateRepository states;private final ApplicationEventPublisher events;private final Clock clock;
    public SlaScanWorker(SlaStateRepository states,ApplicationEventPublisher events,Clock clock){this.states=states;this.events=events;this.clock=clock;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void scan(Long id){var state=states.locked(id).orElse(null);if(state==null)return;var now=clock.instant();
        if(state.breachDue(now)){state.breached(now);events.publishEvent(new SlaBreachedEvent(id,state.getRequestId(),state.getCycle(),now));}
        else if(state.reminderDue(now)){state.reminded(now);events.publishEvent(new SlaDueSoonEvent(id,state.getRequestId(),state.getCycle(),now));}
    }
}
