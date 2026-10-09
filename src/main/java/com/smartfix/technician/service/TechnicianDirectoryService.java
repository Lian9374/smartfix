package com.smartfix.technician.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.domain.TechnicianProfile;
import com.smartfix.technician.dto.TechnicianCandidateResponse;
import com.smartfix.technician.dto.TechnicianProfileResponse;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.repository.TechnicianProfileRepository;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserSummaryResponse;
import com.smartfix.user.service.UserService;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TechnicianDirectoryService {
    private final TechnicianProfileRepository profiles;
    private final UserService users;
    private final LocationService locations;
    private final Validator validator;
    private final Clock clock;

    public TechnicianDirectoryService(TechnicianProfileRepository profiles, UserService users,
            LocationService locations, Validator validator, Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.locations = locations;
        this.validator = validator;
        this.clock = clock;
    }

    /** Reads the current technician's preferences; GET never provisions a database row. */
    public Optional<TechnicianProfileResponse> getProfile(Long actorUserId) {
        requireActiveTechnician(actorUserId);
        return profiles.findByUserId(actorUserId).map(TechnicianDirectoryService::toResponse);
    }

    public List<TechnicianProfileResponse> listProfilesForAdministrator(Long actorId) {
        var actor = users.getUserAccess(actorId);
        if (actor.role() != Role.ADMINISTRATOR || actor.accountStatus() != AccountStatus.ACTIVE)
            throw new AccessDeniedException("Administrator access required.");
        return profiles.findAll().stream().map(TechnicianDirectoryService::toResponse).toList();
    }

    @Transactional
    public TechnicianProfileResponse updateProfile(Long actorUserId, UpdateTechnicianProfileCommand command) {
        requireActiveTechnician(actorUserId);
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new InputValidationException("Select valid skills, service areas and availability.");
        }
        // Cross-module validation uses the public service, never LocationRepository.
        command.getServiceAreaIds().forEach(locations::requireActiveLocation);
        TechnicianProfile profile = profiles.findByUserId(actorUserId).orElse(null);
        if (profile == null) {
            if (command.getVersion() != null) {
                throw staleProfile();
            }
            profile = TechnicianProfile.create(actorUserId, command.getSkills(), command.getServiceAreaIds(),
                    command.getAvailabilityStatus(), clock.instant());
        } else {
            if (!profile.isActive()) {
                throw new AccessDeniedException("This technician profile is inactive.");
            }
            if (!Objects.equals(command.getVersion(), profile.getVersion())) {
                throw staleProfile();
            }
            profile.updatePreferences(command.getSkills(), command.getServiceAreaIds(),
                    command.getAvailabilityStatus(), clock.instant());
        }
        try {
            // Flush inside this method so simultaneous creates/edits become a clear 409.
            return toResponse(profiles.saveAndFlush(profile));
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
            throw staleProfile();
        }
    }

    /** F1-F5 eligibility only; S3-B-02 owns availability/workload recommendation ranking. */
    public List<TechnicianCandidateResponse> findCandidates(MaintenanceCategory category, Long locationId) {
        if (category == null || locationId == null || locationId <= 0) {
            throw new InputValidationException("A category and location are required.");
        }
        locations.requireActiveLocation(locationId);
        List<TechnicianProfile> eligible = profiles.findEligibleProfiles(category, locationId,
                AvailabilityStatus.ON_LEAVE);
        if (eligible.isEmpty()) {
            return List.of();
        }
        // Existing public API supplies account status AND display names without a repository dependency.
        Map<Long, UserSummaryResponse> activeTechnicians = users.listUsers().stream()
                .filter(user -> user.role() == Role.TECHNICIAN && user.accountStatus() == AccountStatus.ACTIVE)
                .collect(Collectors.toMap(UserSummaryResponse::id, Function.identity()));
        return eligible.stream().filter(profile -> activeTechnicians.containsKey(profile.getUserId()))
                .map(profile -> new TechnicianCandidateResponse(profile.getId(), profile.getUserId(),
                        activeTechnicians.get(profile.getUserId()).displayName(), profile.getSkills(),
                        profile.getServiceAreaIds(), profile.getAvailabilityStatus()))
                .toList();
    }

    private void requireActiveTechnician(Long actorUserId) {
        if (actorUserId == null) {
            throw new AccessDeniedException("An active technician account is required.");
        }
        var actor = users.getUserAccess(actorUserId);
        if (actor.role() != Role.TECHNICIAN || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active technician account is required.");
        }
    }

    private static BusinessConflictException staleProfile() {
        return new BusinessConflictException("Your profile changed. Reload the page before saving again.");
    }

    private static TechnicianProfileResponse toResponse(TechnicianProfile profile) {
        return new TechnicianProfileResponse(profile.getId(), profile.getUserId(), profile.getSkills(),
                profile.getServiceAreaIds(), profile.getAvailabilityStatus(), profile.isActive(),
                profile.getVersion(), profile.getUpdatedAt());
    }
}
