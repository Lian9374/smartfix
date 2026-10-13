package com.smartfix.sla.service;
import com.smartfix.sla.repository.SlaStateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Clock;
@Service
public class SlaScanner{
    private final SlaStateRepository states;private final SlaScanWorker worker;private final Clock clock;
    public SlaScanner(SlaStateRepository states,SlaScanWorker worker,Clock clock){this.states=states;this.worker=worker;this.clock=clock;}
    public void scan(){for(Long id:states.candidates(clock.instant(),PageRequest.of(0,50)))try{worker.scan(id);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("SLA scan failed: stateId={}, failureType={}",id,failure.getClass().getSimpleName());}}
}
