package com.smartfix.request.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.dto.*;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.user.domain.*;
import com.smartfix.user.service.UserService;

import jakarta.validation.Validator;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RequestSubmissionService {
    private final MaintenanceRequestRepository requests;
    private final RequestTicketNumberGenerator tickets;
    private final RequestLifecycleService lifecycle;
    private final AttachmentService attachments;
    private final LocationService locations;
    private final UserService users;
    private final Validator validator;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RequestSubmissionService(
            MaintenanceRequestRepository requests,
            RequestTicketNumberGenerator tickets,
            RequestLifecycleService lifecycle,
            AttachmentService attachments,
            LocationService locations,
            UserService users,
            Validator validator,
            Clock clock,
            PlatformTransactionManager manager) {
        this.requests = requests;
        this.tickets = tickets;
        this.lifecycle = lifecycle;
        this.attachments = attachments;
        this.locations = locations;
        this.users = users;
        this.validator = validator;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }

    /** File compensation wraps the actual DB commit, including commit-time exceptions. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MaintenanceRequestSubmissionResponse submit(
            SubmitMaintenanceRequestCommand command, List<MultipartFile> files, Long actorUserId) {
        var actor = users.getUserAccess(actorUserId);
        if (actor.accountStatus() != AccountStatus.ACTIVE || actor.role() != Role.REQUESTER) {
            throw new ResourceNotFoundException("Request not found");
        }
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new InputValidationException("Please complete all required request fields.");
        }
        locations.requireActiveLocation(command.getLocationId());
        // Normalize/validate the aggregate before writing any file.
        if (command.getTitle().trim().isEmpty() || command.getDescription().trim().isEmpty()) {
            throw new InputValidationException("Title and description cannot be blank.");
        }
        List<StoredAttachment> stored;
        try {
            stored = attachments.validateAndStore(files, actorUserId);
        } catch (IllegalArgumentException invalid) {
            throw new InputValidationException("Attachments do not meet the upload requirements.");
        }
        AtomicBoolean committed = new AtomicBoolean(false);
        try {
            return transaction.execute(
                    status -> {
                        TransactionSynchronizationManager.registerSynchronization(
                                new TransactionSynchronization() {
                                    @Override
                                    public int getOrder() {
                                        return Ordered.HIGHEST_PRECEDENCE;
                                    }

                                    @Override
                                    public void afterCommit() {
                                        committed.set(true);
                                    }
                                });
                        // Revalidate mutable account/location data inside the DB transaction.
                        var current = users.getUserAccess(actorUserId);
                        if (current.role() != Role.REQUESTER
                                || current.accountStatus() != AccountStatus.ACTIVE) {
                            throw new ResourceNotFoundException("Request not found");
                        }
                        locations.requireActiveLocation(command.getLocationId());
                        MaintenanceRequest request =
                                requests.saveAndFlush(
                                        MaintenanceRequest.submit(
                                                tickets.nextTicketNumber(),
                                                actorUserId,
                                                command.getLocationId(),
                                                command.getTitle(),
                                                command.getDescription(),
                                                command.getCategory(),
                                                command.getUrgencyLevel(),
                                                clock.instant()));
                        lifecycle.recordInitialSubmission(request.getId(), actorUserId);
                        attachments.saveMetadata(request.getId(), stored);
                        return new MaintenanceRequestSubmissionResponse(request.getTicketNumber());
                    });
        } catch (RuntimeException failure) {
            if (!committed.get()) attachments.deleteStoredFiles(stored);
            throw failure;
        }
    }
}
