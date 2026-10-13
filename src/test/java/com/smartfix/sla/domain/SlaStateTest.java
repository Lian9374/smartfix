package com.smartfix.sla.domain;
import com.smartfix.request.domain.UrgencyLevel;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class SlaStateTest{
    private final Instant start=Instant.parse("2026-10-13T00:00:00Z");
    private SlaPolicy policy(){var p=new SlaPolicy(UrgencyLevel.HIGH,SlaPhase.RESOLUTION);p.configure(3600,600,true);return p;}
    @Test void computesNaturalTimeAndUsesStrictOverdueBoundary(){var state=new SlaState(1L,policy(),start);assertEquals(start.plusSeconds(3600),state.getDueAt());assertFalse(state.breachDue(start.plusSeconds(3600)));assertTrue(state.breachDue(start.plusSeconds(3601)));}
    @Test void pausesAndResumesWithoutCountingHoldTime(){var state=new SlaState(1L,policy(),start);state.pause(start.plusSeconds(100));assertFalse(state.breachDue(start.plusSeconds(10000)));state.resume(start.plusSeconds(1000));assertEquals(start.plusSeconds(4500),state.getDueAt());state.resume(start.plusSeconds(2000));assertEquals(start.plusSeconds(4500),state.getDueAt());}
    @Test void reminderAndEscalationAreOncePerCycle(){var state=new SlaState(1L,policy(),start);assertFalse(state.reminderDue(start.plusSeconds(2999)));assertTrue(state.reminderDue(start.plusSeconds(3000)));state.reminded(start.plusSeconds(3000));assertFalse(state.reminderDue(start.plusSeconds(3500)));state.breached(start.plusSeconds(3601));assertFalse(state.breachDue(start.plusSeconds(5000)));state.restart(policy(),start.plusSeconds(6000));assertEquals(2,state.getCycle());assertNull(state.getBreachedAt());assertNull(state.getRemindedAt());assertEquals(start.plusSeconds(9600),state.getDueAt());}
    @Test void completedTimerCannotEscalate(){var state=new SlaState(1L,policy(),start);state.complete();assertFalse(state.breachDue(start.plusSeconds(10000)));}
    @Test void noProductionDurationOrInvalidPolicyIsAccepted(){var p=policy();assertThrows(IllegalArgumentException.class,()->p.configure(0,0,true));assertThrows(IllegalArgumentException.class,()->p.configure(60,61,true));}
}
