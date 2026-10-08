package com.smartfix.technician.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.domain.TechnicianProfile;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.repository.TechnicianProfileRepository;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.dto.UserSummaryResponse;
import com.smartfix.user.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicianDirectoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    private static ValidatorFactory validation;
    @Mock TechnicianProfileRepository profiles;
    @Mock UserService users;
    @Mock LocationService locations;
    private TechnicianDirectoryService directory;

    @BeforeAll static void validator() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { validation.close(); }

    @BeforeEach
    void service() {
        directory = new TechnicianDirectoryService(profiles, users, locations,
                validation.getValidator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void readingAnUnconfiguredProfileDoesNotCreateIt() {
        activeTechnician();
        assertThat(directory.getProfile(7L)).isEmpty();
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void firstSaveCreatesPreferencesForTheAuthenticatedAccount() {
        activeTechnician();
        when(profiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = directory.updateProfile(7L, validCommand());
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.skills()).containsExactly(MaintenanceCategory.ELECTRICAL);
        assertThat(result.serviceAreaIds()).containsExactly(3L);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.active()).isTrue();
        verify(locations).requireActiveLocation(3L);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"REQUESTER", "ADMINISTRATOR"})
    void nonTechniciansCannotReadOrWriteProfilesEvenThroughTheService(Role role) {
        when(users.getUserAccess(7L)).thenReturn(new UserAccessResponse(7L, role, AccountStatus.ACTIVE, 0));
        assertThatThrownBy(() -> directory.getProfile(7L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> directory.updateProfile(7L, validCommand())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(profiles, locations);
    }

    @Test
    void disabledTechniciansCannotUpdateTheirProfile() {
        when(users.getUserAccess(7L)).thenReturn(new UserAccessResponse(7L, Role.TECHNICIAN, AccountStatus.DISABLED, 1));
        assertThatThrownBy(() -> directory.updateProfile(7L, validCommand())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void serviceValidatesCommandsEvenWithoutMvc() {
        activeTechnician();
        var command = validCommand();
        command.setServiceAreaIds(Set.of(-1L));
        assertThatThrownBy(() -> directory.updateProfile(7L, command)).isInstanceOf(InputValidationException.class);
        command.setServiceAreaIds(Set.of(3L));
        command.setSkills(Set.of());
        assertThatThrownBy(() -> directory.updateProfile(7L, command)).isInstanceOf(InputValidationException.class);
        assertThatThrownBy(() -> directory.updateProfile(7L, null)).isInstanceOf(InputValidationException.class);
        verifyNoInteractions(profiles, locations);
    }

    @Test
    void invalidOrInactiveLocationsRejectTheWholeSave() {
        activeTechnician();
        when(locations.requireActiveLocation(3L)).thenThrow(new InputValidationException("Location is not active"));
        assertThatThrownBy(() -> directory.updateProfile(7L, validCommand())).isInstanceOf(InputValidationException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void anExistingProfileRequiresTheVersionShownOnTheForm() {
        activeTechnician();
        var profile = profile(7L);
        ReflectionTestUtils.setField(profile, "version", 2L);
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
        var command = validCommand();
        command.setAvailabilityStatus(AvailabilityStatus.ON_LEAVE);
        for (Long version : new Long[]{null, 1L, 3L}) {
            command.setVersion(version);
            assertThatThrownBy(() -> directory.updateProfile(7L, command)).isInstanceOf(BusinessConflictException.class);
        }
        assertThat(profile.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void matchingVersionUpdatesPreferencesAndPreservesIdentity() {
        activeTechnician();
        var profile = profile(7L);
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(profiles.saveAndFlush(profile)).thenReturn(profile);
        var command = validCommand();
        command.setVersion(0L);
        command.setAvailabilityStatus(AvailabilityStatus.ON_LEAVE);
        command.setSkills(Set.of(MaintenanceCategory.PLUMBING));
        var result = directory.updateProfile(7L, command);
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.availabilityStatus()).isEqualTo(AvailabilityStatus.ON_LEAVE);
        assertThat(result.skills()).containsExactly(MaintenanceCategory.PLUMBING);
    }

    @Test
    void techniciansCannotReactivateAnInactiveProfile() {
        activeTechnician();
        var profile = profile(7L);
        ReflectionTestUtils.setField(profile, "active", false);
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
        var command = validCommand();
        command.setVersion(0L);
        assertThatThrownBy(() -> directory.updateProfile(7L, command)).isInstanceOf(AccessDeniedException.class);
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void simultaneousFirstSavesReportAConflict() {
        activeTechnician();
        when(profiles.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique user_id"));
        assertThatThrownBy(() -> directory.updateProfile(7L, validCommand()))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("Reload");
    }

    @Test
    void candidateDirectoryExcludesDisabledDemotedAndDeletedAccounts() {
        when(profiles.findEligibleProfiles(MaintenanceCategory.ELECTRICAL, 3L, AvailabilityStatus.ON_LEAVE))
                .thenReturn(List.of(profile(7L), profile(8L), profile(9L), profile(10L)));
        when(users.listUsers()).thenReturn(List.of(
                account(7L, Role.TECHNICIAN, AccountStatus.ACTIVE),
                account(8L, Role.TECHNICIAN, AccountStatus.DISABLED),
                account(9L, Role.REQUESTER, AccountStatus.ACTIVE)));
        var result = directory.findCandidates(MaintenanceCategory.ELECTRICAL, 3L);
        assertThat(result).extracting(candidate -> candidate.userId()).containsExactly(7L);
        assertThat(result.getFirst().displayName()).isEqualTo("Technician 7");
    }

    @Test
    void emptyDirectoryDoesNotLoadUnrelatedAccounts() {
        assertThat(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 3L)).isEmpty();
        verifyNoInteractions(users);
    }

    private void activeTechnician() {
        when(users.getUserAccess(7L)).thenReturn(new UserAccessResponse(7L, Role.TECHNICIAN, AccountStatus.ACTIVE, 0));
    }

    private static TechnicianProfile profile(Long userId) {
        return TechnicianProfile.create(userId, Set.of(MaintenanceCategory.ELECTRICAL), Set.of(3L),
                AvailabilityStatus.AVAILABLE, NOW);
    }

    private static UserSummaryResponse account(Long id, Role role, AccountStatus status) {
        return new UserSummaryResponse(id, "tech" + id, "Technician " + id, role, status, 0, NOW, NOW);
    }

    private static UpdateTechnicianProfileCommand validCommand() {
        var command = new UpdateTechnicianProfileCommand();
        command.setSkills(Set.of(MaintenanceCategory.ELECTRICAL));
        command.setServiceAreaIds(Set.of(3L));
        command.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return command;
    }
}
