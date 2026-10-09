package com.smartfix.dispatch;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.dispatch.dto.*;
import com.smartfix.dispatch.service.AssignmentService;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.StoredAttachment;
import com.smartfix.request.service.*;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.user.domain.*;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.workorder.dto.*;
import com.smartfix.workorder.service.WorkOrderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** B/C/E boundary: real assignments, request/work-order authorization, private files and sessions. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:technician-access-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicianAccessIT {
    private static final Path UPLOADS = temporaryUploads();
    @DynamicPropertySource
    static void uploads(DynamicPropertyRegistry properties) {
        properties.add("smartfix.uploads.dir", () -> UPLOADS.toString());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AssignmentService assignments;
    @Autowired TechnicianDirectoryService directory;
    @Autowired RequestReviewService reviews;
    @Autowired RequestAccessService access;
    @Autowired AttachmentService attachments;
    @Autowired WorkOrderService orders;
    @MockitoSpyBean AttachmentStorageService storage;
    private final Map<Long, MockHttpSession> sessions = new HashMap<>();
    private final List<StoredAttachment> files = new ArrayList<>();
    private Long attachmentId;
    private Long otherAttachmentId;
    private byte[] image;

    @BeforeEach
    void fixtures() throws Exception {
        Files.createDirectories(UPLOADS);
        for (String table : List.of("notifications", "assignments", "repair_records", "request_feedback", "request_status_history",
                "request_attachments", "work_orders", "maintenance_requests", "technician_skills",
                "technician_service_areas", "technician_profiles", "users", "locations")) {
            jdbc.update("DELETE FROM " + table);
        }
        var now = Timestamp.from(Instant.parse("2026-10-08T00:00:00Z"));
        for (long id = 100; id <= 104; id++) {
            jdbc.update("""
                    INSERT INTO users(id,username,display_name,password_hash,role,account_status,
                                      security_version,created_at,updated_at)
                    VALUES(?,?,?,?,?,'ACTIVE',0,?,?)
                    """, id, "access.user" + id, "Test user " + id, "synthetic-test-hash", role(id).name(), now, now);
        }
        jdbc.update("INSERT INTO locations(id,location_code,display_name,active) VALUES(5,'LIB','Library',true)");
        for (long id : List.of(101L, 102L)) {
            var command = new UpdateTechnicianProfileCommand();
            command.setSkills(Set.of(MaintenanceCategory.ELECTRICAL));
            command.setServiceAreaIds(Set.of(5L));
            command.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            directory.updateProfile(id, command);
        }
        request(1, 100);
        request(2, 104);
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        image = output.toByteArray();
        attachmentId = attach(1);
        otherAttachmentId = attach(2);
        clearInvocations(storage);
    }

    @AfterEach
    void removeTestFiles() { attachments.deleteStoredFiles(files); }

    @AfterAll
    static void removeUploadDirectory() throws IOException { Files.deleteIfExists(UPLOADS); }

    @Test
    void onlyCurrentTechnicianCanReadTheRequestWorkOrderAndPrivateAttachment() throws Exception {
        assertRequestDenied(1, 101);
        assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        assertReadable(1, 101);
        var order = order(1);
        assertAllDenied(1, 102, order.id());
        assertThat(orders.findMine(101L, 0, 20).getContent()).extracting(WorkOrderResponse::id).containsExactly(order.id());
        assertThat(orders.findMine(102L, 0, 20).getTotalElements()).isZero();
    }

    @Test
    void requesterAndAdministratorKeepRequestAccessButCannotUseTechnicianActions() throws Exception {
        assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        for (long actor : List.of(100L, 103L)) {
            mvc.perform(get("/requests/" + ticket(1)).session(session(actor))).andExpect(status().isOk());
            mvc.perform(get(attachmentUrl(1)).session(session(actor))).andExpect(status().isOk()).andExpect(content().bytes(image));
            mvc.perform(get("/workorders/" + order(1).id()).session(session(actor))).andExpect(status().isForbidden());
            mvc.perform(post("/workorders/" + order(1).id() + "/accept").session(session(actor)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertRequestDenied(1, 104);
    }

    @Test
    void guessingAnotherRequestOrMixingAttachmentIdsNeverOpensPrivateStorage() throws Exception {
        assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        assignments.assign(ticket(2), new AssignTechnicianCommand(102L), 103L);
        assertRequestDenied(2, 101);
        mvc.perform(get("/requests/" + ticket(1) + "/attachments/" + otherAttachmentId).session(session(101)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/requests/" + ticket(2) + "/attachments/" + attachmentId).session(session(101)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/requests/SF-2026-999999").session(session(101))).andExpect(status().isNotFound());
        mvc.perform(get("/workorders/999999").session(session(101))).andExpect(status().isNotFound());
        verify(storage, never()).loadAsResource(anyString());
    }

    @Test
    void reassignmentRevokesTheExistingSessionAndGrantsTheNewTechnicianAccess() throws Exception {
        var current = assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        assertReadable(1, 101); // Cache only the identity in a real session, never the assignment decision.
        orders.accept(order(1).id(), 101L);
        orders.recordRepair(order(1).id(), 101L, repair());
        assignments.reassign(ticket(1), new ReassignTechnicianCommand(102L, current.id(), "Change shift"), 103L);
        assertAllDenied(1, 101, order(1).id());
        assertThat(listPage(101, 0, 20).getTotalElements()).isZero();
        assertThat(orders.findMine(101L, 0, 20)).isEmpty();
        assertReadable(1, 102);
        assertThat(orders.findRecords(order(1).id(), 102L)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repair_records", Long.class)).isEqualTo(1);
    }

    @Test
    void withdrawalRevokesDetailsDownloadsWritesAndListRowsWithoutDeletingHistory() throws Exception {
        var current = assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        assertReadable(1, 101);
        assignments.withdraw(ticket(1), new WithdrawAssignmentCommand(current.id(), "Review again"), 103L);
        assertAllDenied(1, 101, order(1).id());
        assertThat(listPage(101, 0, 20).getTotalElements()).isZero();
        assertThat(orders.findMine(101L, 0, 20)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assignments", Long.class)).isEqualTo(1);
        assignments.assign(ticket(1), new AssignTechnicianCommand(102L), 103L);
        assertReadable(1, 102);
        assertRequestDenied(1, 101);
    }

    @Test
    void paginationCountsOnlyReadableRowsAndDoesNotLeaveHolesAfterWithdrawal() throws Exception {
        request(3, 100);
        request(4, 100);
        for (long id = 1; id <= 4; id++) assignments.assign(ticket(id), new AssignTechnicianCommand(101L), 103L);
        var fourth = assignments.findActiveAssignment(4L).orElseThrow();
        assignments.withdraw(ticket(4), new WithdrawAssignmentCommand(fourth.id(), "Review again"), 103L);
        var third = assignments.findActiveAssignment(3L).orElseThrow();
        assignments.reassign(ticket(3), new ReassignTechnicianCommand(102L, third.id(), "Change shift"), 103L);
        var firstPage = listPage(101, 0, 1);
        var secondPage = listPage(101, 1, 1);
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(WorkOrderQueueRow::requestId).containsExactly(2L);
        assertThat(secondPage.getContent()).extracting(WorkOrderQueueRow::requestId).containsExactly(1L);
        assertThat(listPage(101, 2, 1)).isEmpty();
        assertThat(listPage(102, 0, 1).getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "roleChanged", "versionChanged"})
    void staleAccountSessionsCannotDownloadAfterAccessChanges(String change) throws Exception {
        assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        assertReadable(1, 101);
        switch (change) {
            case "disabled" -> jdbc.update("UPDATE users SET account_status='DISABLED' WHERE id=101");
            case "roleChanged" -> jdbc.update("UPDATE users SET role='REQUESTER' WHERE id=101");
            case "versionChanged" -> jdbc.update("UPDATE users SET security_version=1 WHERE id=101");
        }
        clearInvocations(storage);
        mvc.perform(get(attachmentUrl(1)).session(session(101)))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?expired"));
        verify(storage, never()).loadAsResource(anyString());
        if (!change.equals("versionChanged")) {
            assertThatThrownBy(() -> access.requireReadableRequest(ticket(1), 101L)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> orders.findMine(101L, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void resolvedWorkRemainsReadableToItsCurrentTechnician() throws Exception {
        assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        Long orderId = order(1).id();
        orders.accept(orderId, 101L);
        orders.recordRepair(orderId, 101L, repair());
        var complete = new CompleteWorkOrderCommand();
        complete.setResolutionNote("Replaced bulb");
        orders.complete(orderId, 101L, complete);
        assertReadable(1, 101);
        assertThat(listPage(101, 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(orders.countOpenWorkOrders(101L)).isZero();
        assertRequestDenied(1, 102);
    }

    @Test
    void anonymousAndMissingCsrfRequestsCannotUseTechnicianResources() throws Exception {
        assignments.assign(ticket(1), new AssignTechnicianCommand(101L), 103L);
        for (String url : List.of("/requests/" + ticket(1), attachmentUrl(1), "/workorders/mine", "/workorders/" + order(1).id())) {
            mvc.perform(get(url)).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
        }
        for (String action : List.of("accept", "records", "complete")) {
            mvc.perform(post("/workorders/" + order(1).id() + "/" + action).session(session(101))).andExpect(status().isForbidden());
        }
        verify(storage, never()).loadAsResource(anyString());
    }

    private void assertReadable(long request, long actor) throws Exception {
        mvc.perform(get("/requests/" + ticket(request)).session(session(actor))).andExpect(status().isOk());
        mvc.perform(get(attachmentUrl(request)).session(session(actor))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(image));
        mvc.perform(get("/workorders/" + order(request).id()).session(session(actor))).andExpect(status().isOk())
                .andExpect(content().string(containsString(ticket(request))));
        assertThat(access.requireReadableRequest(ticket(request), actor).getId()).isEqualTo(request);
    }

    private void assertRequestDenied(long request, long actor) throws Exception {
        clearInvocations(storage);
        mvc.perform(get("/requests/" + ticket(request)).session(session(actor))).andExpect(status().isNotFound());
        mvc.perform(get(attachmentUrl(request)).session(session(actor))).andExpect(status().isNotFound());
        verify(storage, never()).loadAsResource(anyString());
    }

    private void assertAllDenied(long request, long actor, Long orderId) throws Exception {
        assertRequestDenied(request, actor);
        mvc.perform(get("/workorders/" + orderId).session(session(actor))).andExpect(status().isNotFound());
        for (String action : List.of("accept", "records", "complete")) {
            mvc.perform(post("/workorders/" + orderId + "/" + action).session(session(actor)).with(csrf())
                            .param("diagnosis", "Bulb failed").param("actionTaken", "Replaced bulb")
                            .param("minutesSpent", "15").param("resolutionNote", "Repaired"))
                    .andExpect(status().isNotFound());
        }
        assertThatThrownBy(() -> orders.findRecords(orderId, actor)).isInstanceOf(ResourceNotFoundException.class);
    }

    @SuppressWarnings("unchecked")
    private Page<WorkOrderQueueRow> listPage(long actor, int page, int size) throws Exception {
        return (Page<WorkOrderQueueRow>) mvc.perform(get("/workorders/mine").session(session(actor))
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("orders");
    }

    private void request(long id, long requester) {
        var now = Timestamp.from(Instant.parse("2026-10-08T00:00:00Z"));
        jdbc.update("""
                INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,
                                                 category,urgency_level,status,version,created_at,updated_at)
                VALUES(?,?,?,5,?,'Private request description','ELECTRICAL','MEDIUM','SUBMITTED',0,?,?)
                """, id, ticket(id), requester, "Private repair " + id, now, now);
        reviews.review(ticket(id), UrgencyLevel.HIGH, 103L, false, null);
    }

    private Long attach(long request) {
        var stored = attachments.validateAndStore(List.of(new MockMultipartFile("files", "test.png", "image/png", image)), 100L);
        files.addAll(stored);
        return attachments.saveMetadata(request, stored).getFirst().id();
    }

    private WorkOrderResponse order(long request) { return orders.findByRequestId(request).orElseThrow(); }
    private String attachmentUrl(long request) { return "/requests/" + ticket(request) + "/attachments/" + (request == 1 ? attachmentId : otherAttachmentId); }
    private static String ticket(long id) { return String.format(Locale.ROOT, "SF-2026-%06d", id); }
    private static Role role(long id) { return id == 103 ? Role.ADMINISTRATOR : id == 101 || id == 102 ? Role.TECHNICIAN : Role.REQUESTER; }

    private MockHttpSession session(long actor) {
        return sessions.computeIfAbsent(actor, id -> {
            var principal = new SmartFixUserDetails(new UserAuthenticationData(id, "access.user" + id,
                    "synthetic-test-hash", role(id), AccountStatus.ACTIVE, 0L));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
            var session = new MockHttpSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return session;
        });
    }

    private static RepairRecordCommand repair() {
        var repair = new RepairRecordCommand();
        repair.setDiagnosis("Bulb failed");
        repair.setActionTaken("Replaced bulb");
        repair.setMinutesSpent(15);
        return repair;
    }

    private static Path temporaryUploads() {
        try { return Files.createTempDirectory("smartfix-b06-access-"); }
        catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
