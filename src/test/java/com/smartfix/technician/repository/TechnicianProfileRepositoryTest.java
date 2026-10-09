package com.smartfix.technician.repository;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.domain.TechnicianProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/** Executes the V23 technician-table fixture in isolated H2; this does not claim PostgreSQL/Flyway-chain verification. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:technician-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Sql(scripts = {"/db/auth-test-schema.sql", "/db/migration/V3__create_locations.sql",
        "/db/technician-test-schema.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TechnicianProfileRepositoryTest {
    @Autowired TechnicianProfileRepository profiles;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prerequisites() {
        for (long id = 1; id <= 6; id++) {
            jdbc.update("INSERT INTO users (id, username, display_name, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                    id, "tech" + id, "Technician " + id, "synthetic-test-hash", "TECHNICIAN");
        }
        jdbc.update("INSERT INTO locations (id, location_code, display_name) VALUES (10, 'LIB', 'Library'), (20, 'LAB', 'Lab')");
    }

    @Test
    void profileAndCollectionsRoundTripAndUpdatesReplaceRemovedSelections() {
        var profile = save(1L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.AVAILABLE);
        entities.clear();
        var stored = profiles.findByUserId(1L).orElseThrow();
        assertThat(stored.getId()).isEqualTo(profile.getId());
        assertThat(stored.getSkills()).containsExactly(MaintenanceCategory.ELECTRICAL);
        assertThat(stored.getServiceAreaIds()).containsExactly(10L);
        stored.updatePreferences(Set.of(MaintenanceCategory.PLUMBING), Set.of(20L), AvailabilityStatus.BUSY, Instant.now());
        profiles.flush();
        entities.clear();
        var updated = profiles.findByUserId(1L).orElseThrow();
        assertThat(updated.getSkills()).containsExactly(MaintenanceCategory.PLUMBING);
        assertThat(updated.getServiceAreaIds()).containsExactly(20L);
        assertThat(updated.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.BUSY);
        assertThat(updated.getVersion()).isGreaterThan(profile.getVersion());
    }

    @Test
    void candidateQueryAppliesSkillAreaProfileAndLeaveFiltersWithoutExcludingBusyTechnicians() {
        var available = save(1L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.AVAILABLE);
        var busy = save(2L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.BUSY);
        save(3L, MaintenanceCategory.PLUMBING, 10L, AvailabilityStatus.AVAILABLE);
        save(4L, MaintenanceCategory.ELECTRICAL, 20L, AvailabilityStatus.AVAILABLE);
        save(5L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.ON_LEAVE);
        var inactive = save(6L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.AVAILABLE);
        jdbc.update("UPDATE technician_profiles SET active = FALSE WHERE id = ?", inactive.getId());
        entities.clear();
        assertThat(profiles.findEligibleProfiles(MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.ON_LEAVE))
                .extracting(TechnicianProfile::getId).containsExactly(available.getId(), busy.getId());
    }

    @Test
    void databaseRejectsDuplicateProfilesForOneAccount() {
        save(1L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.AVAILABLE);
        assertThatThrownBy(() -> save(1L, MaintenanceCategory.PLUMBING, 20L, AvailabilityStatus.BUSY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownUsersAndLocations() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO technician_profiles (user_id, availability_status) VALUES (999, 'AVAILABLE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        var profile = save(1L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.AVAILABLE);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO technician_service_areas (technician_profile_id, location_id) VALUES (?, 999)", profile.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidAvailabilityCategoryAndDuplicateSelections() {
        var profile = save(1L, MaintenanceCategory.ELECTRICAL, 10L, AvailabilityStatus.AVAILABLE);
        assertThatThrownBy(() -> jdbc.update("UPDATE technician_profiles SET availability_status = 'UNKNOWN' WHERE id = ?", profile.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO technician_skills (technician_profile_id, category) VALUES (?, 'UNKNOWN')", profile.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO technician_skills (technician_profile_id, category) VALUES (?, 'ELECTRICAL')", profile.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO technician_service_areas (technician_profile_id, location_id) VALUES (?, 10)", profile.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TechnicianProfile save(Long userId, MaintenanceCategory skill, Long area, AvailabilityStatus availability) {
        return profiles.saveAndFlush(TechnicianProfile.create(userId, Set.of(skill), Set.of(area), availability, Instant.now()));
    }
}
