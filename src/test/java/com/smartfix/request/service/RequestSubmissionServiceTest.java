package com.smartfix.request.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.*;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.user.domain.*;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.service.UserService;

import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.time.*;
import java.util.*;

class RequestSubmissionServiceTest {
    @Test
    void cleansFilesAfterDefiniteRollback() { submissionFailure(true); }
    @Test void retainsFilesWhenCommitOutcomeIsUnknown() { submissionFailure(false); }
    private void submissionFailure(boolean rolledBack) {
        var requests = mock(MaintenanceRequestRepository.class);
        var tickets = mock(RequestTicketNumberGenerator.class);
        var lifecycle = mock(RequestLifecycleService.class);
        var attachments = mock(AttachmentService.class);
        var locations = mock(LocationService.class);
        var users = mock(UserService.class);
        var validator = mock(Validator.class);
        var manager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        doAnswer(invocation->{
            if(rolledBack)for(var callback:TransactionSynchronizationManager.getSynchronizations())callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            throw new TransactionSystemException("synthetic commit failure");
        })
                .when(manager)
                .commit(transaction);
        when(users.getUserAccess(1L))
                .thenReturn(new UserAccessResponse(1L, Role.REQUESTER, AccountStatus.ACTIVE, 0));
        var c = new SubmitMaintenanceRequestCommand();
        c.setLocationId(2L);
        c.setTitle("Broken light");
        c.setDescription("Fault");
        c.setCategory(MaintenanceCategory.ELECTRICAL);
        c.setUrgencyLevel(UrgencyLevel.HIGH);
        when(validator.validate(c)).thenReturn(Set.of());
        when(tickets.nextTicketNumber()).thenReturn("SF-2026-000001");
        var files =
                List.of(
                        new StoredAttachment(
                                "test.png",
                                "internal.png",
                                "image/png",
                                10,
                                Path.of("test-only.png")));
        when(attachments.validateAndStore(anyList(), eq(1L))).thenReturn(files);
        when(requests.saveAndFlush(any()))
                .thenAnswer(
                        i -> {
                            MaintenanceRequest r = i.getArgument(0);
                            ReflectionTestUtils.setField(r, "id", 10L);
                            return r;
                        });
        var service =
                new RequestSubmissionService(
                        requests,
                        tickets,
                        lifecycle,
                        attachments,
                        locations,
                        users,
                        validator,
                        Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC),
                        manager);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.submit(c, List.of(), 1L))
                    .isInstanceOf(TransactionSystemException.class);
            if(rolledBack)verify(attachments).deleteStoredFiles(files);
            else verify(attachments,never()).deleteStoredFiles(anyList());
            verify(lifecycle).recordInitialSubmission(10L, 1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
