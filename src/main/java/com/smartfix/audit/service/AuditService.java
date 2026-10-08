package com.smartfix.audit.service;

import com.smartfix.audit.domain.AuditEntry;
import com.smartfix.audit.dto.AuditEntryResponse;
import com.smartfix.audit.repository.AuditEntryRepository;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
    private final AuditEntryRepository repository;
    private final UserService users;
    public AuditService(AuditEntryRepository repository, UserService users) {
        this.repository = repository;
        this.users = users;
    }

    /** Fails with the business transaction: a successful moderation cannot lose its audit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String action, String targetType, Long targetId,
                       String outcome, java.time.Instant when) {
        if (!java.util.Set.of("QUESTION", "ANSWER", "REPORT").contains(targetType)) {
            throw new InputValidationException("Unknown audit target.");
        }
        repository.save(new AuditEntry(actorId, action, targetType, targetId, outcome, when));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> list(Long actorId, int page, int size) {
        try {
            var actor = users.getUserAccess(actorId);
            if (actor.accountStatus() != AccountStatus.ACTIVE || actor.role() != Role.ADMINISTRATOR) {
                throw new AccessDeniedException("Administrator access required.");
            }
        } catch (ResourceNotFoundException absent) {
            throw new AccessDeniedException("Administrator access required.");
        }
        return repository.findAllByOrderByOccurredAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.min(50, size <= 0 ? 20 : size)))
                .map(row -> new AuditEntryResponse(row.getId(), row.getActorUserId(), row.getAction(),
                        row.getTargetType(), row.getTargetId(), row.getOutcome(), row.getOccurredAt()));
    }
}
