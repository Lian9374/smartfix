package com.smartfix.audit.repository;

import com.smartfix.audit.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    Page<AuditEntry> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
}
