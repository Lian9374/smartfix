package com.smartfix.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Database-backed yearly ticket counter.
 *
 * <p>The repository obtains this row with a pessimistic write lock. Consequently,
 * {@link #takeNextNumber()} must only be called inside that locked transaction.</p>
 */
@Entity
@Table(name = "request_ticket_sequences")
public class RequestTicketSequence {

    public static final long FIRST_NUMBER = 1L;
    public static final long MAX_NUMBER = 999_999L;

    @Id
    @Column(name = "ticket_year", nullable = false, updatable = false)
    private Integer ticketYear;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    /** Required by JPA. Rows are normally initialized by the repository upsert. */
    protected RequestTicketSequence() {
        // no-op
    }

    private RequestTicketSequence(int ticketYear, long nextValue) {
        this.ticketYear = ticketYear;
        this.nextValue = nextValue;
    }

    public static RequestTicketSequence startForYear(int ticketYear) {
        if (ticketYear < 0 || ticketYear > 9999) {
            throw new IllegalArgumentException("ticketYear must contain four digits");
        }
        return new RequestTicketSequence(ticketYear, FIRST_NUMBER);
    }

    /** Returns the current value and advances the counter for the next caller. */
    public long takeNextNumber() {
        if (nextValue < FIRST_NUMBER || nextValue > MAX_NUMBER) {
            throw new IllegalStateException("The yearly maintenance-request ticket range is exhausted");
        }
        long allocated = nextValue;
        nextValue++;
        return allocated;
    }

    public Integer getTicketYear() {
        return ticketYear;
    }

    public long getNextValue() {
        return nextValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RequestTicketSequence sequence) || ticketYear == null) {
            return false;
        }
        return ticketYear.equals(sequence.ticketYear);
    }

    @Override
    public int hashCode() {
        return RequestTicketSequence.class.hashCode();
    }
}
