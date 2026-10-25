package com.smartfix.notification;

import com.smartfix.audit.repository.AuditEntryRepository;
import com.smartfix.community.event.*;
import com.smartfix.community.domain.*;
import com.smartfix.facility.repository.LocationRepository;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.notification.repository.*;
import com.smartfix.notification.service.*;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.*;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.request.repository.*;
import com.smartfix.request.service.*;
import com.smartfix.sla.domain.*;
import com.smartfix.sla.repository.*;
import com.smartfix.sla.service.*;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.*;
import com.smartfix.user.service.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"smartfix.bootstrap-admin.enabled=false","smartfix.notification.delivery.eager-enabled=false","smartfix.notification.delivery.scheduling-enabled=false","smartfix.notification.delivery.retry-seconds=1","smartfix.notification.delivery.max-attempts=3","smartfix.notification.email.enabled=true"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Import(DeliveryReliabilityPostgresIT.TimeConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeliveryReliabilityPostgresIT {
    static final String SCHEMA="usere_"+UUID.randomUUID().toString().replace("-","");
    static final String PASSWORD="TestPassword9";
    static final Path STORAGE=Path.of("target","usere-attachments-"+SCHEMA).toAbsolutePath();
    static String required(String key){var value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException(key+" required");return value;}
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        String url=required("TEST_DB_URL");if(!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):\\d+/[a-zA-Z0-9_]+_test"))throw new IllegalArgumentException("Dedicated local *_test database required");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");p.add("spring.datasource.username",()->required("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->required("TEST_DB_PASSWORD"));
        p.add("spring.datasource.hikari.schema",()->SCHEMA);p.add("spring.flyway.schemas",()->SCHEMA);p.add("spring.flyway.default-schema",()->SCHEMA);p.add("spring.flyway.enabled",()->true);p.add("spring.jpa.hibernate.ddl-auto",()->"validate");p.add("spring.jpa.properties.hibernate.default_schema",()->SCHEMA);p.add("smartfix.uploads.dir",()->STORAGE.toString());
    }
    @Autowired UserService users;@Autowired NotificationContactService contacts;
    @Autowired DeliveryOutboxService outbox;@Autowired DeliveryDispatcher dispatcher;@Autowired DeliveryWorker worker;
    @Autowired DeliveryAdministrationService administration;@Autowired DeliveryTaskRepository tasks;@Autowired NotificationRepository notifications;
    @Autowired ApplicationEventPublisher events;@Autowired PlatformTransactionManager txManager;@Autowired JdbcTemplate jdbc;
    @Autowired SlaPolicyService sla;@Autowired SlaPolicyRepository policies;@Autowired SlaStateRepository states;@Autowired SlaScanWorker scanner;
    @Autowired RequestSubmissionService submission;@Autowired RequestReadService requests;@Autowired LocationRepository locations;
    @Autowired AttachmentService attachments;@Autowired AttachmentStorageService storage;@Autowired AttachmentRepository attachmentRows;
    @Autowired AuditEntryRepository audit;@Autowired MutableClock clock;@Autowired MockMvc mvc;
    @MockitoBean JavaMailSender mail;
    Long admin,alice,bob,engineer;
    @BeforeAll void accounts(){var command=command("usere.admin",Role.ADMINISTRATOR);assertTrue(users.createInitialAdministrator(command));admin=users.findAuthenticationByUsername("usere.admin").userId();alice=register("usere.alice");bob=register("usere.bob");engineer=users.createUser(command("usere.engineer",Role.TECHNICIAN),admin);users.changeOwnPassword(engineer,PASSWORD,"ChangedPassword9","ChangedPassword9");}
    @BeforeEach void clean(){jdbc.execute("DROP TRIGGER IF EXISTS usere_reject_notification ON notifications");tasks.deleteAll();notifications.deleteAll();audit.deleteAll();states.deleteAll();policies.deleteAll();clock.now=Instant.parse("2026-10-13T00:00:00Z");reset(mail);when(mail.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));contacts.update(admin,alice,"");contacts.update(admin,bob,"");}
    @AfterAll static void cleanup()throws Exception{try(var c=DriverManager.getConnection(required("TEST_DB_URL"),required("TEST_DB_USERNAME"),required("TEST_DB_PASSWORD"));var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}}
    Long register(String name){var c=new RegistrationCommand();c.setUsername(name);c.setDisplayName("Synthetic acceptance user");c.setPassword(PASSWORD);c.setConfirmPassword(PASSWORD);return users.registerRequester(c);}
    CreateUserCommand command(String name,Role role){var c=new CreateUserCommand();c.setUsername(name);c.setDisplayName("Synthetic acceptance account");c.setPassword(PASSWORD);c.setRole(role);return c;}
    void business(Runnable operation){new TransactionTemplate(txManager).executeWithoutResult(s->operation.run());}
    void answer(Long id){events.publishEvent(new CommunityAnswerCreatedEvent(id,800L,alice,bob,clock.instant()));}
    DeliveryTask inApp(){return tasks.findAll().stream().filter(t->t.getChannel().equals("IN_APP")).findFirst().orElseThrow();}
    @Test void rollbackLeavesNeitherOutboxNorNotification(){new TransactionTemplate(txManager).executeWithoutResult(status->{answer(1L);status.setRollbackOnly();});assertEquals(0,tasks.count());assertEquals(0,notifications.count());}
    @Test void committedStageSurvivesConnectionReconnectAndCanBeDelivered(){business(()->answer(1L));assertEquals(2,tasks.count());assertEquals(0,notifications.count());try(var c=DriverManager.getConnection(required("TEST_DB_URL"),required("TEST_DB_USERNAME"),required("TEST_DB_PASSWORD"));var s=c.createStatement();var rows=s.executeQuery("SELECT count(*) FROM "+SCHEMA+".delivery_tasks WHERE status='PENDING'")){assertTrue(rows.next());assertEquals(2,rows.getLong(1));}catch(Exception e){throw new AssertionError(e);}dispatcher.drain();assertEquals(1,notifications.count());assertEquals("DELIVERED",tasks.findById(inApp().getId()).orElseThrow().getStatus());}
    @Test void duplicateEventStagesOnlyOneTaskPerChannel(){business(()->{answer(2L);answer(2L);});business(()->answer(2L));assertEquals(2,tasks.count());dispatcher.drain();dispatcher.drain();assertEquals(1,notifications.count());}
    @Test void concurrentStageAndDeliveryAreIdempotent()throws Exception{var pool=Executors.newFixedThreadPool(2);try{var first=pool.submit(()->business(()->answer(3L)));var second=pool.submit(()->business(()->answer(3L)));first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);assertEquals(2,tasks.count());Long id=inApp().getId();var a=pool.submit(()->worker.deliver(id));var b=pool.submit(()->worker.deliver(id));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);assertEquals(1,notifications.count());assertEquals(1,tasks.findById(id).orElseThrow().getAttempts());}finally{pool.shutdownNow();}}
    @Test void failureDoesNotRollbackBusinessAndSuccessfulRetryRecovers(){jdbc.execute("CREATE OR REPLACE FUNCTION usere_reject_notification() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic delivery failure'; END $$");jdbc.execute("CREATE TRIGGER usere_reject_notification BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION usere_reject_notification()");business(()->answer(4L));Long id=inApp().getId();assertDoesNotThrow(()->worker.deliver(id));assertEquals("PENDING",tasks.findById(id).orElseThrow().getStatus());assertEquals(1,tasks.findById(id).orElseThrow().getAttempts());jdbc.execute("DROP TRIGGER usere_reject_notification ON notifications");clock.now=clock.now.plusSeconds(1);worker.deliver(id);assertEquals("DELIVERED",tasks.findById(id).orElseThrow().getStatus());assertEquals(1,notifications.count());}
    @Test void emailFailureHasFiniteRetriesAndManualRetryAudit(){contacts.update(admin,alice,"fixture@example.test");doThrow(new org.springframework.mail.MailSendException("synthetic")).when(mail).send(any(MimeMessage.class));business(()->answer(5L));var task=tasks.findAll().stream().filter(t->t.getChannel().equals("EMAIL")).findFirst().orElseThrow();worker.deliver(task.getId());clock.now=clock.now.plusSeconds(1);worker.deliver(task.getId());clock.now=clock.now.plusSeconds(5);worker.deliver(task.getId());assertEquals("FAILED",tasks.findById(task.getId()).orElseThrow().getStatus());assertEquals(3,tasks.findById(task.getId()).orElseThrow().getAttempts());worker.deliver(task.getId());verify(mail,times(3)).send(any(MimeMessage.class));reset(mail);when(mail.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));administration.retry(admin,task.getId());worker.deliver(task.getId());assertEquals("DELIVERED",tasks.findById(task.getId()).orElseThrow().getStatus());assertTrue(audit.findAll().stream().anyMatch(a->a.getAction().equals("DELIVERY_RETRY_REQUESTED")));}
    @Test void noContactIsSkippedWithoutExternalMail(){business(()->answer(6L));dispatcher.drain();assertEquals(1,tasks.findAll().stream().filter(t->t.getStatus().equals("SKIPPED")).count());verifyNoInteractions(mail);}
    @Test void selfReplyAndRestoreKeepExistingRecipientRules(){business(()->{events.publishEvent(new CommunityAnswerCreatedEvent(7L,800L,alice,alice,clock.instant()));events.publishEvent(new CommunityContentHiddenEvent(800L,CommunityContentType.QUESTION,800L,alice,admin,CommunityContentStatus.VISIBLE,clock.instant()));});assertEquals(0,tasks.count());}
    @Test void communityAcceptedAndHiddenHaveStableKeys(){business(()->{events.publishEvent(new CommunityAnswerAcceptedEvent(8L,800L,alice,bob,clock.instant()));events.publishEvent(new CommunityContentHiddenEvent(800L,CommunityContentType.QUESTION,800L,alice,admin,CommunityContentStatus.HIDDEN,clock.instant()));});dispatcher.drain();assertEquals(2,notifications.count());assertTrue(notifications.findAll().stream().allMatch(n->n.getMessage().indexOf("PRIVATE_BODY")==-1));}
    @Test void initialSubmissionNullSourceIsDelivered(){business(()->events.publishEvent(new RequestStatusChangedEvent(900000L,"SF-2026-900000",alice,null,RequestStatus.SUBMITTED,alice,clock.instant(),9999L)));dispatcher.drain();assertEquals(1,notifications.count());assertTrue(inApp().getDedupKey().contains("HISTORY:9999"));}
    @Test void onlyAdminCanManageContactPolicyOrRetries(){assertThrows(org.springframework.security.access.AccessDeniedException.class,()->contacts.update(alice,bob,"fixture@example.test"));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->sla.configure(engineer,UrgencyLevel.HIGH,SlaPhase.RESOLUTION,60,10,true));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->administration.list(alice,0));assertThrows(com.smartfix.common.exception.InputValidationException.class,()->contacts.update(admin,bob,"a@example.test\r\nBcc:private@example.test"));}
    @Test void noPolicyReturnsExplicitUnavailableWithoutDeadline(){assertEquals("UNCONFIGURED",sla.read(admin,123L).availability());assertTrue(sla.read(admin,123L).phases().isEmpty());}
    String request(){var c=new SubmitMaintenanceRequestCommand();c.setLocationId(locations.findAllByActiveTrueOrderByDisplayNameAsc().get(0).getId());c.setTitle("Synthetic UserE request");c.setDescription("Synthetic isolated acceptance fixture");c.setCategory(MaintenanceCategory.ELECTRICAL);c.setUrgencyLevel(UrgencyLevel.HIGH);return submission.submit(c,List.of(),alice).ticketNumber();}
    @Test void slaComputesDeadlineFromRealSubmissionAndEscalatesOnceConcurrently()throws Exception{sla.configure(admin,UrgencyLevel.HIGH,SlaPhase.RESOLUTION,60,10,true);String ticket=request();var state=states.findAll().get(0);assertEquals(clock.now.plusSeconds(3600),state.getDueAt());clock.now=clock.now.plusSeconds(3000);scanner.scan(state.getId());scanner.scan(state.getId());assertEquals(1,tasks.findAll().stream().filter(t->t.getEventType().equals("SLA_DUE_SOON")&&t.getChannel().equals("IN_APP")).count());clock.now=clock.now.plusSeconds(601);var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->scanner.scan(state.getId()));var b=pool.submit(()->scanner.scan(state.getId()));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}assertNotNull(states.findById(state.getId()).orElseThrow().getBreachedAt());assertEquals(1,tasks.findAll().stream().filter(t->t.getEventType().equals("SLA_BREACHED")&&t.getChannel().equals("IN_APP")).count());assertEquals(RequestStatus.SUBMITTED,requests.findByTicketNumber(ticket).status());}
    @Test void reopenedResolutionRestartsAndDuplicateHistoryDoesNotRestartAgain(){sla.configure(admin,UrgencyLevel.HIGH,SlaPhase.RESOLUTION,60,0,true);String ticket=request();var id=requests.findByTicketNumber(ticket).id();clock.now=clock.now.plusSeconds(5000);var event=new RequestStatusChangedEvent(id,ticket,alice,RequestStatus.RESOLVED,RequestStatus.REOPENED,alice,clock.now,900000L);business(()->events.publishEvent(event));assertEquals(2,states.findAll().get(0).getCycle());assertEquals(clock.now.plusSeconds(3600),states.findAll().get(0).getDueAt());business(()->events.publishEvent(event));assertEquals(2,states.findAll().get(0).getCycle());}
    @Test void reassignmentPausesAndResumeShiftsDeadline(){sla.configure(admin,UrgencyLevel.HIGH,SlaPhase.RESOLUTION,60,0,true);String ticket=request();Long id=requests.findByTicketNumber(ticket).id();clock.now=clock.now.plusSeconds(60);business(()->events.publishEvent(new RequestStatusChangedEvent(id,ticket,alice,RequestStatus.ASSIGNED,RequestStatus.UNDER_REVIEW,admin,clock.now,800000L)));assertEquals("PAUSED",states.findAll().get(0).getStatus());clock.now=clock.now.plusSeconds(600);business(()->events.publishEvent(new RequestStatusChangedEvent(id,ticket,alice,RequestStatus.UNDER_REVIEW,RequestStatus.ASSIGNED,admin,clock.now,800001L)));assertEquals(Instant.parse("2026-10-13T01:10:00Z"),states.findAll().get(0).getDueAt());}
    @Test void metadataRollbackDeletesOnlyTheNewStoredFile()throws Exception{String ticket=request();Long id=requests.findByTicketNumber(ticket).id();byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aDDcAAAAASUVORK5CYII=");var stored=storage.store(new org.springframework.mock.web.MockMultipartFile("photos","fixture.png","image/png",png));new TransactionTemplate(txManager).executeWithoutResult(status->{attachments.saveMetadata(id,List.of(stored));status.setRollbackOnly();});assertFalse(Files.exists(stored.path()));assertTrue(attachmentRows.findAllByRequestIdOrderByIdAsc(id).isEmpty());}
    @Test void otherRequesterCannotReadPrivateAttachment()throws Exception{String ticket=request();Long id=requests.findByTicketNumber(ticket).id();var file=new StoredAttachment("fixture.png",UUID.randomUUID()+".png","image/png",5,STORAGE.resolve("fixture.png"));var rows=attachments.saveMetadata(id,List.of(file));assertThrows(com.smartfix.common.exception.ResourceNotFoundException.class,()->attachments.readAttachment(ticket,rows.get(0).id(),bob));}
    @Test void adminPagesRenderAndRequesterCannotCallThem()throws Exception{var adminSession=login("usere.admin",PASSWORD);for(String route:List.of("/admin/delivery","/admin/sla","/admin/attachments/inspection"))mvc.perform(get(route).session(adminSession)).andExpect(status().isOk());var requesterSession=login("usere.alice",PASSWORD);for(String route:List.of("/admin/delivery","/admin/sla","/admin/attachments/inspection"))mvc.perform(get(route).session(requesterSession)).andExpect(status().isForbidden());mvc.perform(post("/admin/sla/policies").session(requesterSession).with(csrf()).param("urgency","HIGH").param("phase","RESOLUTION").param("minutes","1").param("reminderMinutes","0")).andExpect(status().isForbidden());}
    org.springframework.mock.web.MockHttpSession login(String name,String password)throws Exception{return (org.springframework.mock.web.MockHttpSession)mvc.perform(post("/login").with(csrf()).param("username",name).param("password",password)).andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);}
    @TestConfiguration static class TimeConfiguration{@Bean @Primary MutableClock usereClock(){return new MutableClock();}}
    static class MutableClock extends Clock{volatile Instant now=Instant.parse("2026-10-13T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
