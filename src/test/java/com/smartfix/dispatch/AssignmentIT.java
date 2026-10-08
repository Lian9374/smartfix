package com.smartfix.dispatch;

import com.smartfix.common.exception.*;
import com.smartfix.dispatch.dto.*;
import com.smartfix.dispatch.event.*;
import com.smartfix.dispatch.service.*;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.RequestTransitionResponse;
import com.smartfix.request.service.*;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.service.*;
import com.smartfix.workorder.domain.WorkOrderStatus;
import com.smartfix.workorder.dto.CompleteWorkOrderCommand;
import com.smartfix.workorder.dto.RepairRecordCommand;
import com.smartfix.workorder.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real B/C services, persistence, active-assignment adapter and transaction commit boundaries. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assignment-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("test")
@Import(AssignmentIT.EventsConfiguration.class)
class AssignmentIT {
    static final String TICKET = "SF-2026-000001";
    static final Timestamp NOW = Timestamp.from(Instant.parse("2026-10-07T00:00:00Z"));
    @Autowired AssignmentService assignments;
    @Autowired AssignmentReadService assignmentReads;
    @Autowired TechnicianRecommendationService recommendations;
    @Autowired TechnicianWorkloadService workload;
    @Autowired RequestReadService requests;
    @Autowired RequestReviewService reviews;
    @Autowired RequestAccessService access;
    @Autowired RequestConfirmationService confirmations;
    @Autowired RequestAssignmentAccessService assignmentAccess;
    @Autowired WorkOrderService workOrders;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CommittedEvents committed;
    @MockitoSpyBean TechnicianDirectoryService directory;
    @MockitoSpyBean WorkOrderLifecycleParticipant workOrderParticipant;

    @BeforeEach
    void fixtures() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().equals("H2")) {
                // H2 cannot parse PostgreSQL partial indexes. Only this test schema uses a generated column.
                // AssignmentPostgresIT inherits these cases against the actual V11 partial unique index.
                jdbc.execute("ALTER TABLE assignments ADD COLUMN IF NOT EXISTS active_request_id BIGINT "
                        + "GENERATED ALWAYS AS (CASE WHEN active THEN request_id ELSE NULL END)");
                jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_assignments_active_request ON assignments(active_request_id)");
            }
        }
        for (String table : List.of("notifications", "assignments", "repair_records", "request_feedback", "request_status_history",
                "request_attachments", "work_orders", "maintenance_requests", "technician_skills",
                "technician_service_areas", "technician_profiles", "users", "locations")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (long id = 100; id <= 104; id++) {
            String role = id == 100 ? "REQUESTER" : id <= 102 ? "TECHNICIAN" : "ADMINISTRATOR";
            jdbc.update("""
                    INSERT INTO users(id,username,display_name,password_hash,role,account_status,
                                      security_version,created_at,updated_at)
                    VALUES(?,?,?,?,?,'ACTIVE',0,?,?)
                    """, id, "assignment.user" + id, "Test user " + id, "synthetic-test-hash", role, NOW, NOW);
        }
        jdbc.update("INSERT INTO locations(id,location_code,display_name,active) VALUES(5,'LIB','Library',true),(6,'LAB','Lab',true)");
        for (long id : List.of(101L, 102L)) {
            var command = new UpdateTechnicianProfileCommand();
            command.setSkills(Set.of(MaintenanceCategory.ELECTRICAL));
            command.setServiceAreaIds(Set.of(5L));
            command.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            directory.updateProfile(id, command);
        }
        jdbc.update("""
                INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,
                                                 category,urgency_level,status,version,created_at,updated_at)
                VALUES(1,?,100,5,'Broken light','Synthetic request','ELECTRICAL','MEDIUM','SUBMITTED',0,?,?)
                """, TICKET, NOW, NOW);
        reviews.review(TICKET, UrgencyLevel.HIGH, 103L, false, null);
        committed.events.clear();
    }

    @Test
    void assignsCreatesOneWorkOrderAndMakesTheRealTechnicianAccessAndWorkloadAvailable() {
        assertThat(assignmentAccess.isAvailable()).isTrue();
        assertThat(workload.countOpenWorkOrders(101L)).isZero();
        var assigned = assign(101);
        assertThat(assigned.technicianId()).isEqualTo(101);
        assertThat(assigned.assignedByUserId()).isEqualTo(103);
        assertThat(assignmentReads.findActiveAssignment(1L)).contains(assigned);
        assertThat(assignments.findActiveAssignment(1L)).contains(assigned);
        assertThat(requests.findById(1L).status()).isEqualTo(RequestStatus.ASSIGNED);
        var order = workOrders.findByRequestId(1L).orElseThrow();
        assertThat(order.technicianId()).isEqualTo(101);
        assertThat(order.status()).isEqualTo(WorkOrderStatus.CREATED);
        assertThat(workOrders.createFor(1L, 101L).id()).isEqualTo(order.id());
        assertThat(count("work_orders")).isEqualTo(1);
        assertThat(count("request_status_history")).isEqualTo(2);
        assertThat(access.requireReadableRequest(TICKET, 101L).getId()).isEqualTo(1);
        assertThatThrownBy(() -> access.requireReadableRequest(TICKET, 102L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(workload.countOpenWorkOrders(101L)).isEqualTo(1);
        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .extracting(TechnicianRecommendationResponse::userId).containsExactly(102L, 101L);
        assertThat(committed.events).singleElement().isEqualTo(new AssignmentCreatedEvent(assigned.id(), 1L,
                TICKET, 100L, 101L, null, 103L, null, assigned.assignedAt()));
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 101})
    void nonAdministratorsCannotPerformAnyAssignmentMutation(long actor) {
        var current = assign(101);
        assertThatThrownBy(() -> assignments.assign(TICKET, new AssignTechnicianCommand(102L), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, current.id(), "Coverage"), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assignments.withdraw(TICKET, new WithdrawAssignmentCommand(current.id(), "Coverage"), actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(assignments.findActiveAssignment(1L)).contains(current);
    }

    @Test
    void disabledAndMissingAdministratorsCannotDispatch() {
        jdbc.update("UPDATE users SET account_status='DISABLED' WHERE id=103");
        assertThatThrownBy(() -> assign(101)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assignments.assign(TICKET, new AssignTechnicianCommand(101L), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(count("assignments")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "wrongRole", "inactiveProfile", "wrongSkill", "wrongArea", "leave", "noProfile"})
    void revalidatesEligibilityEvenIfAFormerRecommendationWasValid(String change) {
        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .extracting(TechnicianRecommendationResponse::userId).contains(101L);
        makeIneligible(change, 101);
        assertThatThrownBy(() -> assign(101)).isInstanceOf(BusinessConflictException.class).hasMessageContaining("eligible");
        assertThat(count("assignments")).isZero();
        assertThat(count("work_orders")).isZero();
        assertThat(requests.findById(1L).status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(committed.events).isEmpty();
    }

    @Test
    void rejectsInvalidCommandsAndUnreviewedRequestsWithoutWrites() {
        assertThatThrownBy(() -> assignments.assign(TICKET, null, 103L)).isInstanceOf(InputValidationException.class);
        assertThatThrownBy(() -> assignments.assign(TICKET, new AssignTechnicianCommand(0L), 103L))
                .isInstanceOf(InputValidationException.class);
        assertThatThrownBy(() -> assignments.assign(" ", new AssignTechnicianCommand(101L), 103L))
                .isInstanceOf(InputValidationException.class);
        jdbc.update("UPDATE maintenance_requests SET final_urgency_level=NULL WHERE id=1");
        assertThatThrownBy(() -> assign(101)).isInstanceOf(BusinessConflictException.class).hasMessageContaining("priority");
        assertThat(count("assignments")).isZero();
        assertThat(count("work_orders")).isZero();
        assertThat(count("request_status_history")).isEqualTo(1);
    }

    @Test
    void duplicateSubmissionAndInvalidRequestStatesDoNotCreateMoreAssignments() {
        assign(101);
        assertThatThrownBy(() -> assign(102)).isInstanceOf(BusinessConflictException.class);
        assertThat(count("assignments")).isEqualTo(1);
        assertThat(count("request_status_history")).isEqualTo(2);
        assertThat(committed.events).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reassignsBeforeOrAfterStartingAndRetainsTheOriginalOrderAndAssignmentHistory(boolean started) {
        var original = assign(101);
        var order = workOrders.findByRequestId(1L).orElseThrow();
        if (started) workOrders.accept(order.id(), 101L);
        var replacement = assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), "  Coverage needed  "), 104L);
        assertThat(replacement.id()).isNotEqualTo(original.id());
        assertThat(replacement.reason()).isEqualTo("Coverage needed");
        assertThat(assignments.findActiveAssignment(1L)).contains(replacement);
        assertThat(jdbc.queryForObject("SELECT active FROM assignments WHERE id=?", Boolean.class, original.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT deactivation_reason FROM assignments WHERE id=?", String.class, original.id()))
                .isEqualTo("Coverage needed");
        assertThat(count("assignments")).isEqualTo(2);
        assertThat(count("work_orders")).isEqualTo(1);
        assertThat(count("request_status_history")).isEqualTo(4);
        var updatedOrder = workOrders.findByRequestId(1L).orElseThrow();
        assertThat(updatedOrder.id()).isEqualTo(order.id());
        assertThat(updatedOrder.technicianId()).isEqualTo(102);
        assertThat(updatedOrder.status()).isEqualTo(WorkOrderStatus.CREATED);
        assertThat(workload.countOpenWorkOrders(101L)).isZero();
        assertThat(workload.countOpenWorkOrders(102L)).isEqualTo(1);
        assertThatThrownBy(() -> access.requireReadableRequest(TICKET, 101L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> workOrders.accept(order.id(), 101L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(access.requireReadableRequest(TICKET, 102L).getId()).isEqualTo(1);
        assertThat(committed.events).hasSize(2);
        assertThat((AssignmentCreatedEvent) committed.events.getLast()).satisfies(e -> {
            assertThat(e.previousTechnicianId()).isEqualTo(101);
            assertThat(e.technicianId()).isEqualTo(102);
            assertThat(e.actorUserId()).isEqualTo(104);
        });
    }

    @Test
    void withdrawsRevokesAccessAndAllowsASeparateNewAssignment() {
        var original = assign(101);
        var withdrawn = assignments.withdraw(TICKET, new WithdrawAssignmentCommand(original.id(), "Recheck scope"), 103L);
        assertThat(withdrawn.active()).isFalse();
        assertThat(withdrawn.deactivatedByUserId()).isEqualTo(103);
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
        assertThat(requests.findById(1L).status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(workOrders.findByRequestId(1L).orElseThrow().status()).isEqualTo(WorkOrderStatus.ON_HOLD);
        assertThat(workload.countOpenWorkOrders(101L)).isZero();
        assertThatThrownBy(() -> access.requireReadableRequest(TICKET, 101L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(committed.events.getLast()).isInstanceOf(AssignmentWithdrawnEvent.class);
        assign(102);
        assertThat(count("assignments")).isEqualTo(2);
        assertThat(count("work_orders")).isEqualTo(1);
    }

    @Test
    void staleReassignmentOrWithdrawalCannotOverrideANewerChoice() {
        var original = assign(101);
        var next = assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), "Coverage"), 103L);
        assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(101L, original.id(), "Old page"), 104L))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("changed");
        assertThatThrownBy(() -> assignments.withdraw(TICKET, new WithdrawAssignmentCommand(original.id(), "Old page"), 104L))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("changed");
        assertThat(assignments.findActiveAssignment(1L)).contains(next);
        assertThat(count("assignments")).isEqualTo(2);
    }

    @Test
    void rejectsBlankLongReasonsSameTechnicianAndIneligibleReplacement() {
        var original = assign(101);
        for (String reason : List.of(" ", "x".repeat(501))) {
            assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), reason), 103L))
                    .isInstanceOf(InputValidationException.class);
            assertThatThrownBy(() -> assignments.withdraw(TICKET, new WithdrawAssignmentCommand(original.id(), reason), 103L))
                    .isInstanceOf(InputValidationException.class);
        }
        assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(101L, original.id(), "Same"), 103L))
                .isInstanceOf(BusinessConflictException.class);
        makeIneligible("disabled", 102);
        assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), "Coverage"), 103L))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("eligible");
        assertThat(assignments.findActiveAssignment(1L)).contains(original);
    }

    @Test
    void startedWorkCannotBeWithdrawnAndResolvedWorkCannotBeReassigned() {
        var current = assign(101);
        var order = workOrders.findByRequestId(1L).orElseThrow();
        workOrders.accept(order.id(), 101L);
        assertThatThrownBy(() -> assignments.withdraw(TICKET, new WithdrawAssignmentCommand(current.id(), "Cancel"), 103L))
                .isInstanceOf(BusinessConflictException.class);
        complete(order.id());
        assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, current.id(), "Coverage"), 103L))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(count("assignments")).isEqualTo(1);
    }

    @Test
    void reopenedWorkCanRetainTheEligibleOriginalTechnicianThroughANewAssignment() {
        var current = assign(101);
        var order = workOrders.findByRequestId(1L).orElseThrow();
        workOrders.accept(order.id(), 101L);
        complete(order.id());
        confirmations.reopen(TICKET, 100L, "Still broken");
        var next = assignments.reassign(TICKET, new ReassignTechnicianCommand(101L, current.id(), "Follow-up repair"), 103L);
        assertThat(next.id()).isNotEqualTo(current.id());
        assertThat(requests.findById(1L).status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(workOrders.findByRequestId(1L).orElseThrow().status()).isEqualTo(WorkOrderStatus.CREATED);
        assertThat(workload.countOpenWorkOrders(101L)).isEqualTo(1);
    }

    @Test
    void rollsBackAssignmentRequestHistoryAndWorkOrderIfCParticipantsFail() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new BusinessConflictException("Synthetic downstream failure");
        }).when(workOrderParticipant).afterTransition(any());
        assertThatThrownBy(() -> assign(101)).isInstanceOf(BusinessConflictException.class);
        assertThat(count("assignments")).isZero();
        assertThat(count("work_orders")).isZero();
        assertThat(count("request_status_history")).isEqualTo(1);
        assertThat(requests.findById(1L).status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(committed.events).isEmpty();
    }

    @Test
    void failedReassignmentRestoresTheOldAssignmentAndOrder() {
        var original = assign(101);
        doAnswer(call -> {
            call.callRealMethod();
            if (((RequestTransitionResponse) call.getArgument(0)).toStatus() == RequestStatus.ASSIGNED) {
                throw new BusinessConflictException("Synthetic downstream failure");
            }
            return null;
        }).when(workOrderParticipant).afterTransition(any());
        assertThatThrownBy(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), "Coverage"), 103L))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(assignments.findActiveAssignment(1L)).contains(original);
        assertThat(requests.findById(1L).status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(workOrders.findByRequestId(1L).orElseThrow().technicianId()).isEqualTo(101);
        assertThat(count("assignments")).isEqualTo(1);
        assertThat(count("request_status_history")).isEqualTo(2);
        assertThat(committed.events).hasSize(1);
    }

    @Test
    void rolledBackOuterTransactionDoesNotEmitACommittedAssignmentNotification() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assign(101);
            assertThat(committed.events).isEmpty();
            status.setRollbackOnly();
        });
        assertThat(count("assignments")).isZero();
        assertThat(count("work_orders")).isZero();
        assertThat(committed.events).isEmpty();
    }

    @Test
    void twoAdministratorsRacingToAssignHaveExactlyOneWinner() throws Exception {
        synchronizeEligibilityChecks();
        var results = race(() -> assignments.assign(TICKET, new AssignTechnicianCommand(101L), 103L),
                () -> assignments.assign(TICKET, new AssignTechnicianCommand(102L), 104L));
        assertOneWinner(results);
        assertThat(count("assignments")).isEqualTo(1);
        assertThat(count("work_orders")).isEqualTo(1);
        assertThat(count("request_status_history")).isEqualTo(2);
        assertThat(committed.events).hasSize(1);
        assertThat(workOrders.findByRequestId(1L).orElseThrow().technicianId())
                .isEqualTo(assignments.findActiveAssignment(1L).orElseThrow().technicianId());
    }

    @Test
    void twoAdministratorsRacingToReassignCannotBothReplaceTheSameAssignment() throws Exception {
        var original = assign(101);
        synchronizeEligibilityChecks();
        var results = race(() -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), "Admin one"), 103L),
                () -> assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, original.id(), "Admin two"), 104L));
        assertOneWinner(results);
        assertThat(count("assignments")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assignments WHERE active", Long.class)).isEqualTo(1);
        assertThat(count("work_orders")).isEqualTo(1);
        assertThat(count("request_status_history")).isEqualTo(4);
        assertThat(committed.events).hasSize(2);
    }

    private void synchronizeEligibilityChecks() {
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
            var result = call.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS); // Both transactions have observed the pre-write state.
            return result;
        }).when(directory).findCandidates(MaintenanceCategory.ELECTRICAL, 5L);
    }

    private List<Object> race(Callable<AssignmentResponse> first, Callable<AssignmentResponse> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> outcome(first));
            var two = executor.submit(() -> outcome(second));
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    private Object outcome(Callable<AssignmentResponse> operation) {
        try { return operation.call(); } catch (Exception error) { return error; }
    }

    private void assertOneWinner(List<Object> results) {
        assertThat(results.stream().filter(AssignmentResponse.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(BusinessConflictException.class::isInstance)).hasSize(1);
    }

    private AssignmentResponse assign(long technician) {
        return assignments.assign(TICKET, new AssignTechnicianCommand(technician), 103L);
    }

    private void complete(Long orderId) {
        var repair = new RepairRecordCommand();
        repair.setDiagnosis("Failed bulb");
        repair.setActionTaken("Replaced bulb");
        repair.setMinutesSpent(15);
        workOrders.recordRepair(orderId, 101L, repair);
        var command = new CompleteWorkOrderCommand();
        command.setResolutionNote("Replaced the light.");
        workOrders.complete(orderId, 101L, command);
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    private void makeIneligible(String change, long userId) {
        switch (change) {
            case "disabled" -> jdbc.update("UPDATE users SET account_status='DISABLED' WHERE id=?", userId);
            case "wrongRole" -> jdbc.update("UPDATE users SET role='REQUESTER' WHERE id=?", userId);
            case "inactiveProfile" -> jdbc.update("UPDATE technician_profiles SET active=false WHERE user_id=?", userId);
            case "wrongSkill" -> jdbc.update("UPDATE technician_skills SET category='PLUMBING' WHERE technician_profile_id IN (SELECT id FROM technician_profiles WHERE user_id=?)", userId);
            case "wrongArea" -> jdbc.update("UPDATE technician_service_areas SET location_id=6 WHERE technician_profile_id IN (SELECT id FROM technician_profiles WHERE user_id=?)", userId);
            case "leave" -> jdbc.update("UPDATE technician_profiles SET availability_status='ON_LEAVE' WHERE user_id=?", userId);
            case "noProfile" -> {
                jdbc.update("DELETE FROM technician_skills WHERE technician_profile_id IN (SELECT id FROM technician_profiles WHERE user_id=?)", userId);
                jdbc.update("DELETE FROM technician_service_areas WHERE technician_profile_id IN (SELECT id FROM technician_profiles WHERE user_id=?)", userId);
                jdbc.update("DELETE FROM technician_profiles WHERE user_id=?", userId);
            }
            default -> throw new IllegalArgumentException(change);
        }
    }

    static class CommittedEvents {
        final List<Object> events = new CopyOnWriteArrayList<>();
        @TransactionalEventListener public void created(AssignmentCreatedEvent event) { events.add(event); }
        @TransactionalEventListener public void withdrawn(AssignmentWithdrawnEvent event) { events.add(event); }
    }

    @TestConfiguration
    static class EventsConfiguration {
        @Bean CommittedEvents committedAssignmentEvents() { return new CommittedEvents(); }
    }
}
