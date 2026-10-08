package com.smartfix.request.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.dto.RequestSnapshotResponse;
import com.smartfix.request.repository.MaintenanceRequestRepository;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class RequestReadService {
    private final MaintenanceRequestRepository requests;

    public RequestReadService(MaintenanceRequestRepository requests) {
        this.requests = requests;
    }

    public RequestSnapshotResponse findById(Long id) {
        var r =
                requests.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        return response(r);
    }

    public RequestSnapshotResponse findByTicketNumber(String ticket) {
        return response(
                requests.findByTicketNumber(ticket)
                        .orElseThrow(() -> new ResourceNotFoundException("Request not found")));
    }

    public Page<RequestSnapshotResponse> findByStatus(RequestStatus status, int page, int size) {
        return requests.findByStatus(status, pageable(page, size)).map(this::response);
    }

    /**
     * Half-open [start,end) interval lets D aggregate adjacent reporting periods without counting
     * boundaries twice.
     */
    public Page<RequestSnapshotResponse> findCreatedBetween(
            Instant start, Instant end, int page, int size) {
        if (start == null || end == null || !start.isBefore(end))
            throw new InputValidationException("A valid reporting interval is required.");
        return requests.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        start, end, pageable(page, size))
                .map(this::response);
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(0, page),
                size <= 0 ? 20 : Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private RequestSnapshotResponse response(MaintenanceRequest r) {
        return new RequestSnapshotResponse(
                r.getId(),
                r.getTicketNumber(),
                r.getRequesterId(),
                r.getLocationId(),
                r.getCategory(),
                r.getEffectiveUrgencyLevel(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getResolvedAt(),
                r.getClosedAt());
    }

    public long countByStatus(RequestStatus status) {
        return requests.countByStatus(status);
    }
}
