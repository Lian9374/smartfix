package com.smartfix.request;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.*;
import com.smartfix.request.config.AttachmentProperties;
import com.smartfix.request.config.RequestWorkflowProperties;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.*;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.request.repository.*;
import com.smartfix.request.service.*;
import com.smartfix.request.spi.ActiveAssignmentLookup;
import com.smartfix.user.domain.*;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.workorder.dto.*;
import com.smartfix.workorder.service.WorkOrderService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * Real C services, persistence, templates and file storage. Only B's unfinished adapter/ticket SQL
 * are test doubles.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:smartfix-c-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(RequestWorkflowTest.Collaboration.class)
class RequestWorkflowTest {
    private static final Path UPLOADS = tempDirectory();
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    // PostgreSQL JDBC does not infer a SQL type for Instant; H2 accepts it, which hid this fixture issue.
    private static final java.sql.Timestamp JDBC_NOW = java.sql.Timestamp.from(NOW);

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry r) {
        r.add("smartfix.uploads.dir", () -> UPLOADS.toString());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RequestSubmissionService submissions;
    @Autowired RequestReviewService reviews;
    @Autowired RequestLifecycleService lifecycle;
    @Autowired RequestConfirmationService confirmations;
    @Autowired RequestAccessService access;
    @Autowired RequestQueryService queries;
    @Autowired MaintenanceRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired WorkOrderService workorders;
    @Autowired RequestWorkflowProperties properties;
    @Autowired AttachmentProperties uploadLimits;
    @Autowired AssignmentFixture assignments;
    @Autowired CommittedEvents committed;
    @Autowired MockMvc mvc;
    @MockitoSpyBean AttachmentService attachmentService;
    @MockitoBean RequestTicketNumberGenerator tickets;
    @MockitoBean Clock clock;
    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void fixtures() throws Exception {
        for (String table :
                List.of(
                        "notifications",
                        "repair_records",
                        "request_feedback",
                        "request_status_history",
                        "request_attachments",
                        "work_orders",
                        "maintenance_requests",
                        "request_ticket_sequences",
                        "users",
                        "locations")) jdbc.update("DELETE FROM " + table);
        for (int id = 1; id <= 5; id++) {
            String role = id == 3 ? "ADMINISTRATOR" : id >= 4 ? "TECHNICIAN" : "REQUESTER";
            jdbc.update(
                    "INSERT INTO"
                        + " users(id,username,display_name,password_hash,role,account_status,security_version,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,'ACTIVE',0,?,?)",
                    id,
                    "test.user" + id,
                    "Test User " + id,
                    "synthetic-test-hash",
                    role,
                    JDBC_NOW,
                    JDBC_NOW);
        }
        jdbc.update(
                "INSERT INTO locations(id,location_code,display_name,active)"
                        + " VALUES(1,'TEST-01','Test location',true)");
        when(tickets.nextTicketNumber())
                .thenAnswer(i -> "SF-2026-%06d".formatted(sequence.incrementAndGet()));
        when(clock.instant()).thenReturn(NOW);
        properties.setReopenWindow(Duration.ZERO);
        assignments.current.clear();
        committed.events.clear();
        try (var files = Files.list(UPLOADS)) {
            for (Path path : files.toList()) Files.delete(path);
        }
    }

    @Test
    void submitsFilesHistoryAndRendersTheRequesterPages() throws Exception {
        var image = image();
        var result = submissions.submit(command(), List.of(image), 1L);
        var r = requests.findByTicketNumber(result.ticketNumber()).orElseThrow();
        assertThat(history.findAllByRequestIdOrderByChangedAtAsc(r.getId()))
                .singleElement()
                .satisfies(
                        h -> {
                            assertThat(h.getFromStatus()).isNull();
                            assertThat(h.getToStatus()).isEqualTo(RequestStatus.SUBMITTED);
                        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_attachments", Long.class))
                .isEqualTo(1L);
        try (var files = Files.list(UPLOADS)) {
            assertThat(files.count()).isEqualTo(1);
        }
        assertThat(committed.events)
                .singleElement()
                .satisfies(e -> assertThat(e.toStatus()).isEqualTo(RequestStatus.SUBMITTED));
        mvc.perform(get("/requests/new").with(user(principal(1, Role.REQUESTER))))
                .andExpect(status().isOk());
        mvc.perform(get("/requests/mine").with(user(principal(1, Role.REQUESTER))))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/requests/" + result.ticketNumber())
                                .with(user(principal(1, Role.REQUESTER))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Photos")));
        var attachmentId = jdbc.queryForObject("SELECT id FROM request_attachments", Long.class);
        mvc.perform(
                        get("/requests/" + result.ticketNumber() + "/attachments/" + attachmentId)
                                .with(user(principal(1, Role.REQUESTER))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(image.getBytes()));
        mvc.perform(
                        get("/requests/" + result.ticketNumber())
                                .with(user(principal(2, Role.REQUESTER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void configuredUploadLimitsAppearOnTheFormAndRejectExcessFilesWithoutLosingInput()
            throws Exception {
        int previous = uploadLimits.getMaxFiles();
        try {
            uploadLimits.setMaxFiles(1);
            mvc.perform(get("/requests/new").with(user(principal(1, Role.REQUESTER))))
                    .andExpect(status().isOk())
                    .andExpect(
                            content()
                                    .string(
                                            org.hamcrest.Matchers.containsString(
                                                    "Up to 1 PNG/JPEG images")));
            mvc.perform(
                            multipart("/requests")
                                    .file(image())
                                    .file(image())
                                    .param("locationId", "1")
                                    .param("title", "Keep this title")
                                    .param("description", "Keep this description")
                                    .param("category", "ELECTRICAL")
                                    .param("urgencyLevel", "MEDIUM")
                                    .with(user(principal(1, Role.REQUESTER)))
                                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasErrors("command"))
                    .andExpect(
                            content()
                                    .string(
                                            org.hamcrest.Matchers.containsString(
                                                    "Keep this title")))
                    .andExpect(
                            content()
                                    .string(
                                            org.hamcrest.Matchers.containsString(
                                                    "Keep this description")))
                    .andExpect(
                            content()
                                    .string(
                                            org.hamcrest.Matchers.containsString(
                                                    "Up to 1 PNG/JPEG images")));
            assertThat(requests.count()).isZero();
            assertThat(history.count()).isZero();
            assertThat(committed.events).isEmpty();
            try (var files = Files.list(UPLOADS)) {
                assertThat(files.count()).isZero();
            }
        } finally {
            uploadLimits.setMaxFiles(previous);
        }
    }

    @Test
    void invalidAttachmentsLeaveNoRowsFilesOrEvents() throws Exception {
        var image = image();
        assertThatThrownBy(
                        () ->
                                submissions.submit(
                                        command(), List.of(image, image, image, image), 1L))
                .isInstanceOf(InputValidationException.class);
        assertThat(requests.count()).isZero();
        assertThat(history.count()).isZero();
        assertThat(committed.events).isEmpty();
        try (var files = Files.list(UPLOADS)) {
            assertThat(files.count()).isZero();
        }
        mvc.perform(
                        multipart("/requests")
                                .file(image)
                                .param("title", "Keep this title")
                                .with(user(principal(1, Role.REQUESTER)))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("Keep this title")));
    }

    @Test
    void metadataFailureRollsBackRequestHistoryAndCleansFiles() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic failure"))
                .when(attachmentService)
                .saveMetadata(anyLong(), anyList());
        assertThatThrownBy(() -> submissions.submit(command(), List.of(image()), 1L))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(requests.count()).isZero();
        assertThat(history.count()).isZero();
        assertThat(committed.events).isEmpty();
        try (var files = Files.list(UPLOADS)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void completeFlowReopensTheSameWorkOrderAndPreservesRecords() throws Exception {
        String ticket = submitted();
        var id = id(ticket);
        reviews.review(ticket, UrgencyLevel.HIGH, 3L, false, null);
        assertThat(requests.findById(id).orElseThrow().getFinalUrgencyLevel())
                .isEqualTo(UrgencyLevel.HIGH);
        assignments.assign(id, 4L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        var order = workorders.createFor(id, 4L);
        workorders.accept(order.id(), 4L);
        workorders.recordRepair(order.id(), 4L, repair());
        workorders.complete(order.id(), 4L, completion());
        confirmations.confirm(ticket, 1L);
        confirmations.feedback(ticket, 1L, 5, "Helpful repair");
        confirmations.reopen(ticket, 1L, "The fault returned.");
        assertThat(workorders.findByRequestId(id).orElseThrow().id()).isEqualTo(order.id());
        assertThat(workorders.findRecords(order.id(), 4L)).hasSize(1);
        workorders.accept(order.id(), 4L);
        workorders.recordRepair(order.id(), 4L, repair());
        workorders.complete(order.id(), 4L, completion());
        confirmations.confirm(ticket, 1L);
        confirmations.close(ticket, 3L);
        assertThat(lifecycle.getStatus(ticket)).isEqualTo(RequestStatus.CLOSED);
        assertThat(workorders.findByRequestId(id).orElseThrow().status().name())
                .isEqualTo("CLOSED");
        assertThat(workorders.findRecords(order.id(), 4L)).hasSize(2);
        assertThatThrownBy(() -> confirmations.reopen(ticket, 1L, null))
                .isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> confirmations.feedback(ticket, 1L, 4, null))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(history.findAllByRequestIdOrderByChangedAtAsc(id)).hasSize(11);
        mvc.perform(get("/requests/" + ticket).with(user(principal(1, Role.REQUESTER))))
                .andExpect(status().isOk());
        mvc.perform(get("/workorders/" + order.id()).with(user(principal(4, Role.TECHNICIAN))))
                .andExpect(status().isOk());
        mvc.perform(get("/workorders/mine").with(user(principal(4, Role.TECHNICIAN))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsIllegalRolesMissingReasonsAndUnassignedTechnicians() {
        String ticket = submitted();
        int count = committed.events.size();
        assertThatThrownBy(() -> lifecycle.transition(ticket, RequestStatus.RESOLVED, 1L, "skip"))
                .isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> confirmations.confirm(ticket, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        reviews.review(ticket, UrgencyLevel.LOW, 3L, false, null);
        assertThatThrownBy(() -> lifecycle.transition(ticket, RequestStatus.REJECTED, 3L, " "))
                .isInstanceOf(InputValidationException.class);
        assertThatThrownBy(() -> lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null))
                .isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> access.requireReadableRequest(ticket, 4L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(history.count()).isEqualTo(2);
        assertThat(committed.events).hasSize(count + 1);
    }

    @Test
    void failedRejectionRollsBackReviewAndItsEvent() {
        String ticket = submitted();
        int eventCount = committed.events.size();
        assertThatThrownBy(() -> reviews.review(ticket, UrgencyLevel.HIGH, 3L, true, " "))
                .isInstanceOf(InputValidationException.class);
        assertThat(lifecycle.getStatus(ticket)).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(history.count()).isEqualTo(1);
        assertThat(committed.events).hasSize(eventCount);
    }

    @Test
    void reassignmentRevokesTheOldTechnician() {
        String ticket = submitted();
        Long id = id(ticket);
        reviews.review(ticket, UrgencyLevel.MEDIUM, 3L, false, null);
        assignments.assign(id, 4L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        var order = workorders.findByRequestId(id).orElseThrow();
        workorders.accept(order.id(), 4L);
        assignments.assign(id, 5L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, "Changed technician");
        assertThat(workorders.findMine(4L, 0, 20).getTotalElements()).isZero();
        assertThat(workorders.findMine(5L, 0, 20).getContent())
                .extracting(w -> w.id()).containsExactly(order.id());
        assertThatThrownBy(() -> workorders.recordRepair(order.id(), 4L, repair()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> access.requireReadableRequest(ticket, 4L))
                .isInstanceOf(ResourceNotFoundException.class);
        workorders.accept(order.id(), 5L);
        assertThat(workorders.findReadable(order.id(), 5L).technicianId()).isEqualTo(5L);
    }

    @Test
    void withdrawalRevokesAccessAndCancellationClosesTheSuspendedOrder() {
        String ticket = submitted();
        Long requestId = id(ticket);
        reviews.review(ticket, UrgencyLevel.MEDIUM, 3L, false, null);
        assignments.assign(requestId, 4L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        assertThat(workorders.countOpenWorkOrders(4L)).isEqualTo(1);
        assignments.current.remove(requestId);
        lifecycle.transition(ticket, RequestStatus.UNDER_REVIEW, 3L, "Withdrawn");
        assertThat(workorders.countOpenWorkOrders(4L)).isZero();
        assertThatThrownBy(() -> access.requireReadableRequest(ticket, 4L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(workorders.findMine(4L, 0, 20).getTotalElements()).isZero();
        confirmations.cancel(ticket, 1L);
        assertThat(workorders.findByRequestId(requestId).orElseThrow().status().name())
                .isEqualTo("CLOSED");
    }

    @Test
    void withdrawnOrdersAreExcludedBeforePaginationAndCounting() throws Exception {
        String first = submitted();
        String second = submitted();
        String third = submitted();
        for (String ticket : List.of(first, second, third)) {
            reviews.review(ticket, UrgencyLevel.HIGH, 3L, false, null);
            assignments.assign(id(ticket), 4L);
            lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        }
        assignments.current.remove(id(third));
        lifecycle.transition(third, RequestStatus.UNDER_REVIEW, 3L, "Withdrawn");
        var page = workorders.findMine(4L, 0, 1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(w -> w.ticketNumber()).containsExactly(second);
        assertThat(workorders.findMine(4L, 1, 1).getContent())
                .extracting(w -> w.ticketNumber()).containsExactly(first);
        mvc.perform(get("/workorders/mine").with(user(principal(4, Role.TECHNICIAN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(third))));
    }

    @Test
    void configuredReopenWindowCanBeChangedWithoutEditingStateRules() {
        String ticket = resolved();
        properties.setReopenWindow(Duration.ofDays(2));
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(3)));
        assertThatThrownBy(() -> confirmations.reopen(ticket, 1L, null))
                .isInstanceOf(BusinessConflictException.class);
        properties.setReopenWindow(Duration.ZERO);
        confirmations.reopen(ticket, 1L, null);
        assertThat(lifecycle.getStatus(ticket)).isEqualTo(RequestStatus.REOPENED);
    }

    @Test
    void concurrentCompletionHasOneWinnerAndOneResolutionHistory() throws Exception {
        String ticket = submitted();
        Long id = id(ticket);
        reviews.review(ticket, UrgencyLevel.HIGH, 3L, false, null);
        assignments.assign(id, 4L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        Long orderId = workorders.findByRequestId(id).orElseThrow().id();
        workorders.accept(orderId, 4L);
        workorders.recordRepair(orderId, 4L, repair());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt =
                    () -> {
                        start.await();
                        try {
                            workorders.complete(orderId, 4L, completion());
                            return true;
                        } catch (BusinessConflictException
                                | org.springframework.dao.OptimisticLockingFailureException
                                        expected) {
                            return false;
                        }
                    };
            Future<Boolean> first = pool.submit(attempt), second = pool.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(
                        history.findAllByRequestIdOrderByChangedAtAsc(id).stream()
                                .filter(h -> h.getToStatus() == RequestStatus.RESOLVED)
                                .count())
                .isEqualTo(1);
    }

    @Test
    void pagesRenderRoleActionsAndEscapeUserText() throws Exception {
        String ticket = submitted();
        reviews.review(ticket, UrgencyLevel.MEDIUM, 3L, false, null);
        mvc.perform(
                        get("/requests/" + ticket + "/review")
                                .with(user(principal(3, Role.ADMINISTRATOR))))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/requests").with(user(principal(3, Role.ADMINISTRATOR))))
                .andExpect(status().isOk());
        assignments.assign(id(ticket), 4L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        var order = workorders.findByRequestId(id(ticket)).orElseThrow();
        mvc.perform(get("/workorders/" + order.id()).with(user(principal(4, Role.TECHNICIAN))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Request summary")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Broken light")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test location")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;fault&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>fault</script>"))));
        workorders.accept(order.id(), 4L);
        mvc.perform(get("/workorders/" + order.id()).with(user(principal(4, Role.TECHNICIAN))))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/workorders/" + order.id() + "/complete")
                                .with(user(principal(4, Role.TECHNICIAN)))
                                .with(csrf())
                                .param("resolutionNote", " "))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/requests/" + ticket + "/confirm")
                                .with(user(principal(1, Role.REQUESTER))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/requests/" + ticket).with(user(principal(1, Role.REQUESTER))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "<script>fault</script>"))));
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of("SUBMITTED", "UNDER_REVIEW", 3L),
                Arguments.of("SUBMITTED", "CANCELLED", 1L),
                Arguments.of("UNDER_REVIEW", "CANCELLED", 1L),
                Arguments.of("UNDER_REVIEW", "ASSIGNED", 3L),
                Arguments.of("UNDER_REVIEW", "REJECTED", 3L),
                Arguments.of("ASSIGNED", "IN_PROGRESS", 4L),
                Arguments.of("ASSIGNED", "UNDER_REVIEW", 3L),
                Arguments.of("IN_PROGRESS", "ASSIGNED", 3L),
                Arguments.of("IN_PROGRESS", "RESOLVED", 4L),
                Arguments.of("RESOLVED", "CONFIRMED", 1L),
                Arguments.of("CONFIRMED", "CLOSED", 3L),
                Arguments.of("RESOLVED", "REOPENED", 1L),
                Arguments.of("CONFIRMED", "REOPENED", 1L),
                Arguments.of("REOPENED", "ASSIGNED", 3L),
                Arguments.of("REOPENED", "IN_PROGRESS", 4L));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void supportsEveryPlannedTransitionAndRecordsItsActor(String from, String to, Long actor) {
        String ticket = submitted();
        Long id = id(ticket);
        jdbc.update(
                "UPDATE maintenance_requests SET"
                        + " status=?,final_urgency_level='HIGH',resolved_at=?,confirmed_at=? WHERE"
                        + " id=?",
                from,
                JDBC_NOW,
                JDBC_NOW,
                id);
        if (!from.equals("SUBMITTED") && !from.equals("UNDER_REVIEW")) {
            String workStatus =
                    from.equals("ASSIGNED")
                            ? "CREATED"
                            : from.equals("REOPENED") ? "REOPENED" : "COMPLETED";
            jdbc.update(
                    "INSERT INTO"
                        + " work_orders(request_id,technician_id,status,version,created_at,updated_at,resolution_note,completed_at)"
                        + " VALUES(?,4,?,0,?,?,?,?)",
                    id,
                    workStatus,
                    JDBC_NOW,
                    JDBC_NOW,
                    "Resolved",
                    JDBC_NOW);
        }
        if (!to.equals("UNDER_REVIEW")) assignments.assign(id, 4L);
        lifecycle.transition(ticket, RequestStatus.valueOf(to), actor, "Transition reason");
        assertThat(lifecycle.getStatus(ticket).name()).isEqualTo(to);
        assertThat(history.findAllByRequestIdOrderByChangedAtAsc(id))
                .hasSize(2)
                .last()
                .satisfies(
                        h -> {
                            assertThat(h.getFromStatus().name()).isEqualTo(from);
                            assertThat(h.getToStatus().name()).isEqualTo(to);
                            assertThat(h.getChangedByUserId()).isEqualTo(actor);
                        });
    }

    private String submitted() {
        return submissions.submit(command(), List.of(), 1L).ticketNumber();
    }

    private Long id(String ticket) {
        return requests.findByTicketNumber(ticket).orElseThrow().getId();
    }

    private String resolved() {
        String ticket = submitted();
        reviews.review(ticket, UrgencyLevel.HIGH, 3L, false, null);
        assignments.assign(id(ticket), 4L);
        lifecycle.transition(ticket, RequestStatus.ASSIGNED, 3L, null);
        var w = workorders.findByRequestId(id(ticket)).orElseThrow();
        workorders.accept(w.id(), 4L);
        workorders.recordRepair(w.id(), 4L, repair());
        workorders.complete(w.id(), 4L, completion());
        return ticket;
    }

    private SubmitMaintenanceRequestCommand command() {
        var c = new SubmitMaintenanceRequestCommand();
        c.setLocationId(1L);
        c.setTitle("Broken light");
        c.setDescription("<script>fault</script>");
        c.setCategory(MaintenanceCategory.ELECTRICAL);
        c.setUrgencyLevel(UrgencyLevel.MEDIUM);
        return c;
    }

    private RepairRecordCommand repair() {
        var c = new RepairRecordCommand();
        c.setDiagnosis("Failed bulb");
        c.setActionTaken("Replaced bulb");
        c.setMinutesSpent(15);
        return c;
    }

    private CompleteWorkOrderCommand completion() {
        var c = new CompleteWorkOrderCommand();
        c.setResolutionNote("Light works again.");
        return c;
    }

    private SmartFixUserDetails principal(long id, Role role) {
        return new SmartFixUserDetails(
                new UserAuthenticationData(
                        id, "test.user" + id, "hash", role, AccountStatus.ACTIVE, 0));
    }

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("files", "test.png", "image/png", output.toByteArray());
    }

    private static Path tempDirectory() {
        try {
            return Files.createTempDirectory("smartfix-c-test-");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        try (var files = Files.list(UPLOADS)) {
            for (Path p : files.toList()) Files.delete(p);
        }
        Files.delete(UPLOADS);
    }

    static class AssignmentFixture implements ActiveAssignmentLookup {
        final Map<Long, ActiveAssignment> current = new ConcurrentHashMap<>();

        void assign(Long requestId, Long technicianId) {
            current.put(requestId, new ActiveAssignment(requestId, requestId, technicianId));
        }

        public Optional<ActiveAssignment> findActiveAssignment(Long requestId) {
            return Optional.ofNullable(current.get(requestId));
        }
    }

    static class CommittedEvents {
        final List<RequestStatusChangedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(RequestStatusChangedEvent event) {
            events.add(event);
        }
    }

    @TestConfiguration
    static class Collaboration {
        @Bean
        @Primary
        AssignmentFixture assignmentFixture() {
            return new AssignmentFixture();
        }

        @Bean
        CommittedEvents committedEvents() {
            return new CommittedEvents();
        }
    }
}
