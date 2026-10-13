package com.smartfix.sla.repository;
import com.smartfix.sla.domain.*;
import com.smartfix.request.domain.UrgencyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy,Long>{
    Optional<SlaPolicy> findByUrgencyAndPhase(UrgencyLevel urgency,SlaPhase phase);
    List<SlaPolicy> findByEnabledTrueAndUrgency(UrgencyLevel urgency);
    boolean existsByEnabledTrue();
}
