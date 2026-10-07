package com.smartfix.dispatch;

import com.smartfix.dispatch.dto.TechnicianRecommendationResponse;
import com.smartfix.dispatch.service.TechnicianRecommendationService;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.spi.ActiveAssignmentLookup;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.technician.service.TechnicianWorkloadService;
import com.smartfix.workorder.domain.WorkOrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/** Real directory, workload, C services and JPA; only B-03's pending assignment lookup is a fixture. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:technician-recommendation-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("test")
@Import(TechnicianRecommendationIT.Collaboration.class)
@Transactional
class TechnicianRecommendationIT {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    @Autowired TechnicianRecommendationService recommendations;
    @Autowired TechnicianWorkloadService workload;
    @Autowired TechnicianDirectoryService directory;
    @Autowired AssignmentFixture assignments;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void fixtures() {
        assignments.current.clear();
        for (int id = 100; id <= 110; id++) {
            jdbc.update("""
                    INSERT INTO users(id,username,display_name,password_hash,role,account_status,
                                      security_version,created_at,updated_at)
                    VALUES(?,?,?,?,?,'ACTIVE',0,?,?)
                    """, id, "recommendation.user" + id, "Technician " + id, "synthetic-test-hash",
                    id == 100 ? "REQUESTER" : "TECHNICIAN", NOW, NOW);
        }
        jdbc.update("""
                INSERT INTO locations(id,location_code,display_name,active)
                VALUES(5,'LIB','Library',true),(6,'LAB','Laboratory',true)
                """);
    }

    @Test
    void excludesEveryIneligibleProfileWhileKeepingBusyTechnicians() {
        profile(10, 101, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        profile(20, 102, AvailabilityStatus.BUSY, MaintenanceCategory.ELECTRICAL, 5);
        profile(30, 103, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        profile(40, 104, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        profile(50, 105, AvailabilityStatus.AVAILABLE, MaintenanceCategory.PLUMBING, 5);
        profile(60, 106, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 6);
        profile(70, 107, AvailabilityStatus.ON_LEAVE, MaintenanceCategory.ELECTRICAL, 5);
        profile(80, 108, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        jdbc.update("UPDATE users SET account_status='DISABLED' WHERE id=103"); // F1
        jdbc.update("UPDATE technician_profiles SET active=false WHERE id=40"); // F2
        jdbc.update("UPDATE users SET role='REQUESTER' WHERE id=108");
        // 105: wrong skill (F3), 106: wrong area (F4), 107: leave (F5), 109/110: no profile.
        order(1, 101, WorkOrderStatus.IN_PROGRESS);

        var result = recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L);

        assertThat(result).extracting(TechnicianRecommendationResponse::userId).containsExactly(101L, 102L);
        // Availability outranks workload: the busy candidate with no work still comes last.
        assertThat(result).extracting(TechnicianRecommendationResponse::openWorkOrders).containsExactly(1L, 0L);
        assertThat(result.getFirst().displayName()).isEqualTo("Technician 101");
        assertThat(result.getFirst().skills()).containsExactly(MaintenanceCategory.ELECTRICAL);
        assertThat(result.getFirst().serviceAreaIds()).containsExactly(5L);
        assertThat(recommendations.recommend(MaintenanceCategory.HVAC, 5L)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"CREATED,1", "IN_PROGRESS,1", "ON_HOLD,1", "REOPENED,1", "COMPLETED,0", "CLOSED,0"})
    void usesTheRealWorkOrderStatusCount(WorkOrderStatus status, long expected) {
        order(1, 101, status);
        // Another technician's open order must never inflate this technician's count.
        order(2, 102, WorkOrderStatus.IN_PROGRESS);

        assertThat(workload.countOpenWorkOrders(101L)).isEqualTo(expected);
        assertThat(workload.countOpenWorkOrders(102L)).isEqualTo(1);
        assertThat(workload.countOpenWorkOrders(103L)).isZero();
    }

    @Test
    void withdrawnReassignedAndMismatchedAssignmentsDoNotCountAsCurrentWork() {
        order(1, 101, WorkOrderStatus.CREATED);
        order(2, 101, WorkOrderStatus.IN_PROGRESS);
        order(3, 101, WorkOrderStatus.REOPENED);
        order(4, 101, WorkOrderStatus.ON_HOLD);
        assertThat(workload.countOpenWorkOrders(101L)).isEqualTo(4);

        assignments.current.remove(1L);
        assignments.assign(2, 102); // Old row still names 101: no longer their assignment.
        assignments.current.put(3L, new ActiveAssignmentLookup.ActiveAssignment(3L, 999L, 101L));
        assertThat(workload.countOpenWorkOrders(101L)).isEqualTo(1);
        // Count the new technician only once C has synchronized the actual work-order row.
        assertThat(workload.countOpenWorkOrders(102L)).isZero();
        jdbc.update("UPDATE work_orders SET technician_id=102 WHERE request_id=2");
        entityManager.clear();
        assertThat(workload.countOpenWorkOrders(102L)).isEqualTo(1);
        assertThat(workload.countOpenWorkOrders(101L)).isEqualTo(1);
    }

    @Test
    void reranksAfterCompletionWithdrawalAndProfileChangesUsingProfileIdForTies() {
        // User ids run in the opposite order from profile ids to catch accidental identity swaps.
        profile(10, 103, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        profile(20, 102, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        profile(30, 101, AvailabilityStatus.AVAILABLE, MaintenanceCategory.ELECTRICAL, 5);
        order(1, 103, WorkOrderStatus.IN_PROGRESS);
        order(2, 103, WorkOrderStatus.CREATED);
        order(3, 102, WorkOrderStatus.ON_HOLD);
        order(4, 101, WorkOrderStatus.REOPENED);

        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .extracting(TechnicianRecommendationResponse::profileId).containsExactly(20L, 30L, 10L);
        jdbc.update("UPDATE work_orders SET status='COMPLETED',completed_at=? WHERE id=1", NOW);
        entityManager.clear();
        var tied = recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L);
        assertThat(tied).extracting(TechnicianRecommendationResponse::profileId).containsExactly(10L, 20L, 30L);
        assertThat(tied).extracting(TechnicianRecommendationResponse::openWorkOrders).containsExactly(1L, 1L, 1L);
        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L)).isEqualTo(tied);

        assignments.current.remove(4L);
        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .extracting(TechnicianRecommendationResponse::userId).containsExactly(101L, 103L, 102L);
        changeAvailability(103, AvailabilityStatus.ON_LEAVE);
        changeAvailability(101, AvailabilityStatus.BUSY);
        assertThat(recommendations.recommend(MaintenanceCategory.ELECTRICAL, 5L))
                .extracting(TechnicianRecommendationResponse::userId).containsExactly(102L, 101L);
    }

    private void changeAvailability(long userId, AvailabilityStatus availability) {
        var current = directory.getProfile(userId).orElseThrow();
        var command = new UpdateTechnicianProfileCommand();
        command.setSkills(current.skills());
        command.setServiceAreaIds(current.serviceAreaIds());
        command.setAvailabilityStatus(availability);
        command.setVersion(current.version());
        directory.updateProfile(userId, command);
    }

    private void profile(long id, long userId, AvailabilityStatus availability,
            MaintenanceCategory skill, long area) {
        jdbc.update("""
                INSERT INTO technician_profiles(id,user_id,availability_status,active,version,created_at,updated_at)
                VALUES(?,?,?,true,0,?,?)
                """, id, userId, availability.name(), NOW, NOW);
        jdbc.update("INSERT INTO technician_skills(technician_profile_id,category) VALUES(?,?)", id, skill.name());
        jdbc.update("INSERT INTO technician_service_areas(technician_profile_id,location_id) VALUES(?,?)", id, area);
    }

    /** Seed lifecycle outcomes directly: C owns and separately tests the state transitions. */
    private void order(long id, long technicianUserId, WorkOrderStatus status) {
        String requestStatus = switch (status) {
            case CREATED -> "ASSIGNED";
            case IN_PROGRESS, ON_HOLD -> "IN_PROGRESS";
            case REOPENED -> "REOPENED";
            case COMPLETED -> "RESOLVED";
            case CLOSED -> "CLOSED";
        };
        jdbc.update("""
                INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,
                                                 category,urgency_level,status,version,created_at,updated_at)
                VALUES(?,?,100,5,'Test repair','Synthetic test request','ELECTRICAL','MEDIUM',?,0,?,?)
                """, id, "SF-2026-%06d".formatted(id), requestStatus, NOW, NOW);
        jdbc.update("""
                INSERT INTO work_orders(id,request_id,technician_id,status,version,created_at,updated_at)
                VALUES(?,?,?,?,0,?,?)
                """, id, id, technicianUserId, status.name(), NOW, NOW);
        assignments.assign(id, technicianUserId);
    }

    @TestConfiguration
    static class Collaboration {
        @Bean @Primary AssignmentFixture assignmentFixture() { return new AssignmentFixture(); }
    }

    /** Test-only active assignments; production persistence/adapter belongs to S3-B-03. */
    static class AssignmentFixture implements ActiveAssignmentLookup {
        final Map<Long, ActiveAssignment> current = new HashMap<>();

        void assign(long requestId, long technicianUserId) {
            current.put(requestId, new ActiveAssignment(requestId, requestId, technicianUserId));
        }

        @Override
        public Optional<ActiveAssignment> findActiveAssignment(Long requestId) {
            return Optional.ofNullable(current.get(requestId));
        }
    }
}
