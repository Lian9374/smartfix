package com.smartfix.sla.event;
import java.time.Instant;
public record SlaDueSoonEvent(Long stateId,Long requestId,int cycle,Instant occurredAt){}
