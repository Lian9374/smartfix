package com.smartfix.request.repository;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatusHistory;
import com.smartfix.request.domain.UrgencyLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
class RequestRepositoryTest {

    private static final Long REQUESTER_ONE_ID = 11L;
    private static final Long REQUESTER_TWO_ID = 12L;
    private static final Long LOCATION_ID = 21L;

    @Autowired
    private MaintenanceRequestRepository requestRepository;

    @Autowired
    private RequestStatusHistoryRepository historyRepository;

    @Test
    void findsRequestByTicketNumber() {
        MaintenanceRequest request = createRequest(
                "SF-2026-000001",
                REQUESTER_ONE_ID,
                Instant.parse("2026-09-24T01:00:00Z")
        );
        requestRepository.saveAndFlush(request);

        Optional<MaintenanceRequest> result =
                requestRepository.findByTicketNumber("SF-2026-000001");

        assertThat(result)
                .isPresent()
                .get()
                .extracting(MaintenanceRequest::getRequesterId)
                .isEqualTo(REQUESTER_ONE_ID);
    }

    @Test
    void returnsEmptyWhenTicketNumberDoesNotExist() {
        Optional<MaintenanceRequest> result =
                requestRepository.findByTicketNumber("SF-2026-999999");

        assertThat(result).isEmpty();
    }

    @Test
    void findsOnlyRequesterOwnedRequestsInNewestFirstOrder() {
        requestRepository.save(createRequest(
                "SF-2026-000001",
                REQUESTER_ONE_ID,
                Instant.parse("2026-09-24T01:00:00Z")
        ));
        requestRepository.save(createRequest(
                "SF-2026-000002",
                REQUESTER_ONE_ID,
                Instant.parse("2026-09-24T03:00:00Z")
        ));
        requestRepository.save(createRequest(
                "SF-2026-000003",
                REQUESTER_TWO_ID,
                Instant.parse("2026-09-24T04:00:00Z")
        ));
        requestRepository.flush();

        List<MaintenanceRequest> result =
                requestRepository.findAllByRequesterIdOrderByCreatedAtDesc(
                        REQUESTER_ONE_ID,
                        PageRequest.of(0, 10)
                );

        assertThat(result)
                .extracting(MaintenanceRequest::getTicketNumber)
                .containsExactly(
                        "SF-2026-000002",
                        "SF-2026-000001"
                );
    }

    @Test
    void findsStatusHistoryInChronologicalOrder() {
        MaintenanceRequest request = requestRepository.saveAndFlush(
                createRequest(
                        "SF-2026-000001",
                        REQUESTER_ONE_ID,
                        Instant.parse("2026-09-24T01:00:00Z")
                )
        );

        historyRepository.save(RequestStatusHistory.initialSubmission(
                request.getId(),
                REQUESTER_ONE_ID,
                Instant.parse("2026-09-24T03:00:00Z")
        ));
        historyRepository.save(RequestStatusHistory.initialSubmission(
                request.getId(),
                REQUESTER_ONE_ID,
                Instant.parse("2026-09-24T01:00:00Z")
        ));
        historyRepository.save(RequestStatusHistory.initialSubmission(
                999L,
                REQUESTER_TWO_ID,
                Instant.parse("2026-09-24T02:00:00Z")
        ));
        historyRepository.flush();

        List<RequestStatusHistory> result =
                historyRepository.findAllByRequestIdOrderByChangedAtAsc(
                        request.getId()
                );

        assertThat(result)
                .extracting(RequestStatusHistory::getChangedAt)
                .containsExactly(
                        Instant.parse("2026-09-24T01:00:00Z"),
                        Instant.parse("2026-09-24T03:00:00Z")
                );
    }

    private MaintenanceRequest createRequest(
            String ticketNumber,
            Long requesterId,
            Instant submittedAt
    ) {
        return MaintenanceRequest.submit(
                ticketNumber,
                requesterId,
                LOCATION_ID,
                "Air conditioner leaking",
                "Water is dripping beside the window.",
                MaintenanceCategory.HVAC,
                UrgencyLevel.HIGH,
                submittedAt
        );
    }
}