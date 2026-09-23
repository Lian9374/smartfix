package com.smartfix.request.repository;

import com.smartfix.request.domain.RequestTicketSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence and locking for the yearly maintenance-request ticket counter. */
public interface RequestTicketSequenceRepository extends JpaRepository<RequestTicketSequence, Integer> {

    /**
     * Creates the row for a new year without racing another application instance.
     * PostgreSQL's conflict handling is atomic; the following locked read serializes
     * allocation from the row whether this call inserted it or another transaction did.
     */
    @Modifying
    @Query(value = """
            INSERT INTO request_ticket_sequences (ticket_year, next_value)
            VALUES (:ticketYear, 1)
            ON CONFLICT (ticket_year) DO NOTHING
            """, nativeQuery = true)
    void ensureYearExists(@Param("ticketYear") int ticketYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RequestTicketSequence s where s.ticketYear = :ticketYear")
    Optional<RequestTicketSequence> findByTicketYearForUpdate(@Param("ticketYear") int ticketYear);
}
