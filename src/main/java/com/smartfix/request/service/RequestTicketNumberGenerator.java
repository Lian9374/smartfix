package com.smartfix.request.service;

import com.smartfix.request.domain.RequestTicketSequence;
import com.smartfix.request.repository.RequestTicketSequenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;

/** Generates unique human-readable ticket numbers in {@code SF-YYYY-NNNNNN} form. */
@Service
public class RequestTicketNumberGenerator {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Singapore");

    private final RequestTicketSequenceRepository sequenceRepository;
    private final Clock clock;

    /**
     * Uses the team's shared {@code Clock} bean when category B provides it. Until
     * then, the production-safe fallback still evaluates the year in Singapore time.
     */
    @Autowired
    public RequestTicketNumberGenerator(RequestTicketSequenceRepository sequenceRepository,
                                        ObjectProvider<Clock> clockProvider) {
        this(sequenceRepository, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    RequestTicketNumberGenerator(RequestTicketSequenceRepository sequenceRepository, Clock clock) {
        this.sequenceRepository = sequenceRepository;
        this.clock = clock;
    }

    /**
     * Allocates the next number while holding the database row lock.
     *
     * <p>The method joins the request-creation transaction. It also starts a transaction
     * when exercised independently so the pessimistic lock can never be acquired outside
     * a transaction.</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String nextTicketNumber() {
        int ticketYear = Year.now(clock.withZone(BUSINESS_ZONE)).getValue();
        sequenceRepository.ensureYearExists(ticketYear);
        RequestTicketSequence sequence = sequenceRepository.findByTicketYearForUpdate(ticketYear)
                .orElseThrow(() -> new IllegalStateException(
                        "The maintenance-request ticket counter could not be initialized"));
        long sequenceNumber = sequence.takeNextNumber();
        return "SF-%04d-%06d".formatted(ticketYear, sequenceNumber);
    }
}
