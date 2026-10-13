package com.smartfix.sla.repository;
import com.smartfix.sla.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface SlaStateRepository extends JpaRepository<SlaState,Long>{
    boolean existsByRequestId(Long id);
    List<SlaState> findByRequestIdOrderByPhaseAsc(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SlaState s where s.requestId=:request order by s.phase")
    List<SlaState> lockedForRequest(@Param("request") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SlaState s where s.id=:id")
    Optional<SlaState> locked(@Param("id") Long id);
    @Query("select s.id from SlaState s where s.status='ACTIVE' and s.breachedAt is null and (s.dueAt<:now or (s.remindedAt is null and s.reminderSeconds>0 and s.reminderDueAt<=:now)) order by s.dueAt,s.id")
    List<Long> candidates(@Param("now") Instant now,Pageable page);
    Page<SlaState> findAllByOrderByDueAtAscIdAsc(Pageable page);
}
