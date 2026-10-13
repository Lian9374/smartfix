package com.smartfix.notification.repository;
import com.smartfix.notification.domain.DeliveryTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask,Long> {
    boolean existsByDedupKey(String key);
    @Query("select t.id from DeliveryTask t where t.status='PENDING' and t.nextAttemptAt<=:now order by t.nextAttemptAt,t.id")
    List<Long> due(@Param("now") Instant now,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from DeliveryTask t where t.id=:id")
    Optional<DeliveryTask> locked(@Param("id") Long id);
    Page<DeliveryTask> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
}
