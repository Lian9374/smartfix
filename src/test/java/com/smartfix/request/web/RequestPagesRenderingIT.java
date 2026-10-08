package com.smartfix.request.web;

import com.smartfix.facility.domain.Location;
import com.smartfix.facility.repository.LocationRepository;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatusHistory;
import com.smartfix.request.domain.UrgencyLevel;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.request.repository.RequestStatusHistoryRepository;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the pages that only Thymeleaf can break.
 *
 * <p>{@code RequestQueryControllerTest} asserts view <em>names</em> against a
 * mocked service, and the controller tests for user management do the same, so
 * between them no test ever asks the template engine to produce these four
 * pages. A bad expression - a record property read through the wrong accessor, a
 * fragment called with the wrong arity, a nested conditional - is a runtime
 * failure that type-checking never sees, so it would reach a browser long before
 * it reached a test.</p>
 *
 * <p>This test closes that gap: real controllers, real services, real templates,
 * real Spring Security, H2 underneath. It asserts that the pages render at all
 * and that the details each page exists to show are actually present - the
 * ticket link, the status and urgency labels, the timeline, the per-row account
 * controls. It deliberately says nothing about CSS classes beyond the few the
 * markup depends on to mean anything.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-request-pages-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestPagesRenderingIT {

    // Synthetic test-only credential, never an application default.
    private static final String PASSWORD = "TestPassword9";

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private LocationRepository locations;
    @Autowired private MaintenanceRequestRepository requests;
    @Autowired private RequestStatusHistoryRepository history;

    private Long administratorId;
    private Long requesterId;
    private Long otherRequesterId;
    private Long overviewRequesterId;
    private Long emptyRequesterId;

    @BeforeAll
    void accounts() {
        administratorId = createAccount("root.admin", Role.ADMINISTRATOR);
        requesterId = createAccount("alice", Role.REQUESTER);
        otherRequesterId = createAccount("bob", Role.REQUESTER);
        // Two accounts reserved for the overview tests. They have to be their own
        // accounts: this class shares one database across its tests, so a request
        // seeded for alice would change what the list assertions above expect to
        // find on her page, and a request seeded for bob would fill the empty
        // state he exists to demonstrate.
        overviewRequesterId = createAccount("carol", Role.REQUESTER);
        emptyRequesterId = createAccount("dave", Role.REQUESTER);
    }

    @Test
    void newRequestUsesTheSharedShellAndKeepsCreationOutOfNavigation() throws Exception {
        String page = mvc.perform(get("/requests/new").session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New maintenance request")))
                .andExpect(content().string(containsString("href=\"/css/site.css\"")))
                .andExpect(content().string(containsString("src=\"/js/request-form.js\"")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"locationId\"")))
                .andExpect(content().string(containsString("name=\"files\"")))
                .andExpect(content().string(containsString("Heating &amp; air conditioning")))
                .andReturn().getResponse().getContentAsString();
        String navigation = page.substring(page.indexOf("<nav class=\"appbar__links\""),
                page.indexOf("</nav>", page.indexOf("<nav class=\"appbar__links\"")));
        org.junit.jupiter.api.Assertions.assertFalse(navigation.contains("/requests/new"));
        org.junit.jupiter.api.Assertions.assertTrue(navigation.contains("aria-current=\"page\""));
    }

    @Test
    void invalidSubmissionRetainsTextAndShowsFieldErrorsInTheSharedShell() throws Exception {
        mvc.perform(post("/requests").session(login("alice")).with(csrf())
                        .param("title", "Leaking pantry tap")
                        .param("description", "The tap drips constantly."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your request has not been submitted")))
                .andExpect(content().string(containsString("Location is required.")))
                .andExpect(content().string(containsString("Category is required.")))
                .andExpect(content().string(containsString("value=\"Leaking pantry tap\"")))
                .andExpect(content().string(containsString("The tap drips constantly.")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("Please select your photos again")));
    }

    @Test
    void overviewShowsTheRequestersOwnMostRecentRequests() throws Exception {
        seed("SF-2026-000105", overviewRequesterId, "Broken blinds in the reading room",
                "The blinds in the reading room no longer close and let in full sun.",
                MaintenanceCategory.BUILDING, UrgencyLevel.LOW);

        mvc.perform(get("/").session(login("carol")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Overview")))
                // The greeting names the account the session actually carries.
                .andExpect(content().string(containsString("Welcome back, carol.")))
                .andExpect(content().string(containsString("Recent requests")))
                .andExpect(content().string(containsString("View all requests")))
                .andExpect(content().string(containsString("SF-2026-000105")))
                .andExpect(content().string(containsString("Broken blinds in the reading room")))
                .andExpect(content().string(containsString("href=\"/requests/mine\"")))
                // A requester's overview shows their own tickets only.
                .andExpect(content().string(not(containsString("SF-2026-000101"))))
                .andExpect(content().string(not(containsString("SF-2026-000102"))))
                // Roles read as words in the product's own casing, not as the
                // constants the backend stores.
                .andExpect(content().string(containsString("Requester")))
                .andExpect(content().string(not(containsString(">REQUESTER<"))));
    }

    @Test
    void overviewTellsARequesterWithNoRequestsWhatTheListWillHold() throws Exception {
        mvc.perform(get("/").session(login("dave")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome back, dave.")))
                .andExpect(content().string(containsString("No requests yet")))
                .andExpect(content().string(containsString(
                        "Your maintenance requests will appear here.")))
                // Submission is implemented and authorised for a requester, so
                // the overview offers the form instead of a note announcing that
                // it is missing. The link is the real route the submission
                // controller serves, not a placeholder.
                .andExpect(content().string(containsString("href=\"/requests/new\"")))
                .andExpect(content().string(not(containsString(
                        "Online submission is currently unavailable."))));
    }

    @Test
    void overviewGivesAnAdministratorTheirOwnEntriesAndNoRequestList() throws Exception {
        mvc.perform(get("/").session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administration")))
                // The queue is a real, authorised administrator route, so the
                // overview offers it beside the lookup rather than leaving the
                // administrator to guess the path.
                .andExpect(content().string(containsString("href=\"/admin/requests\"")))
                .andExpect(content().string(containsString("href=\"/admin/users\"")))
                .andExpect(content().string(containsString("href=\"/admin/requests/lookup\"")))
                // No service exposes "every request", so no list is invented here.
                .andExpect(content().string(not(containsString("Recent requests"))))
                // An administrator never sees the requester-only entry points.
                .andExpect(content().string(not(containsString("href=\"/requests/new\""))));
    }

    @Test
    void myRequestsListsTheRequestersOwnRequestWithItsRealFields() throws Exception {
        seed("SF-2026-000101", requesterId, "Flickering ceiling light",
                "The ceiling light above the main entrance flickers all evening.",
                MaintenanceCategory.ELECTRICAL, UrgencyLevel.HIGH);

        mvc.perform(get("/requests/mine").session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Requests")))
                .andExpect(content().string(containsString(
                        "Track your maintenance requests and their status.")))
                // The current section is marked for assistive technology.
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                // Ticket number links straight to the detail page.
                .andExpect(content().string(containsString("href=\"/requests/SF-2026-000101\"")))
                .andExpect(content().string(containsString("Flickering ceiling light")))
                .andExpect(content().string(containsString("COM1 Level 1 Lobby")))
                .andExpect(content().string(containsString("ELECTRICAL")))
                .andExpect(content().string(containsString("HIGH")))
                // Status and urgency are printed as words, not only coloured.
                .andExpect(content().string(containsString("SUBMITTED")))
                .andExpect(content().string(containsString("Request history")))
                // The card no longer restates the heading above it.
                .andExpect(content().string(not(containsString("Submitted requests"))))
                .andExpect(content().string(not(containsString("No requests yet"))))
                // C3 supplies submission, filtering and real pagination metadata.
                .andExpect(content().string(containsString("href=\"/requests/new\"")))
                .andExpect(content().string(containsString("name=\"status\"")))
                .andExpect(content().string(containsString("1 request")));
    }

    @Test
    void myRequestsExplainsItselfWhenTheRequesterHasSubmittedNothing() throws Exception {
        mvc.perform(get("/requests/mine").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No requests yet")))
                .andExpect(content().string(containsString(
                        "Your maintenance requests will appear here.")))
                // The C3 submission form is available even when there are no requests.
                .andExpect(content().string(containsString("href=\"/requests/new\"")))
                .andExpect(content().string(not(containsString("Online submission is currently unavailable."))))
                .andExpect(content().string(not(containsString("Submitted requests"))))
                // An empty list has no count to show.
                .andExpect(content().string(not(containsString("0 shown"))))
                .andExpect(content().string(containsString("0 requests")));
    }

    @Test
    void requestDetailRendersTheDescriptionAndTheStatusHistoryForItsOwner() throws Exception {
        seed("SF-2026-000102", requesterId, "Leaking tap in the pantry",
                "The cold-water tap drips constantly and has soaked the cabinet.",
                MaintenanceCategory.PLUMBING, UrgencyLevel.MEDIUM);

        mvc.perform(get("/requests/SF-2026-000102").session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Leaking tap in the pantry")))
                .andExpect(content().string(containsString("Ticket SF-2026-000102")))
                .andExpect(content().string(containsString(
                        "The cold-water tap drips constantly and has soaked the cabinet.")))
                .andExpect(content().string(containsString("Status history")))
                // The history reads as a transition in words, not as a bare dot.
                .andExpect(content().string(containsString("Recorded as")))
                .andExpect(content().string(containsString("1 entry")))
                // The shared UI states the request's parent in its title and breadcrumb.
                .andExpect(content().string(containsString("My Requests / SF-2026-000102")))
                // A requester already knows the request is theirs.
                .andExpect(content().string(not(containsString("<dt>Requester</dt>"))))
                // The way back is the requester's own list.
                .andExpect(content().string(containsString("href=\"/requests/mine\"")))
                .andExpect(content().string(not(containsString(
                        "href=\"/admin/requests/lookup\""))));
    }

    @Test
    void requestDetailOffersTheAdministratorTheRequesterAndTheLookupRoute() throws Exception {
        seed("SF-2026-000103", otherRequesterId, "Projector will not power on",
                "The projector in seminar room 3 does not power on.",
                MaintenanceCategory.BUILDING, UrgencyLevel.LOW);

        mvc.perform(get("/requests/SF-2026-000103").session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Projector will not power on")))
                .andExpect(content().string(containsString("<dt>Requester</dt>")))
                .andExpect(content().string(containsString("Account #" + otherRequesterId)))
                .andExpect(content().string(containsString("Back to request lookup")))
                .andExpect(content().string(not(containsString("Back to my requests"))));
    }

    @Test
    void adminLookupShowsAnEmptyStateBeforeAnyTicketNumberIsEntered() throws Exception {
        mvc.perform(get("/admin/requests/lookup").session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No request opened yet")))
                .andExpect(content().string(not(containsString("Request summary"))))
                // No result means no "clear result" action either.
                .andExpect(content().string(not(containsString("Clear result"))));
    }

    @Test
    void adminLookupRendersTheRequestBehindTheTicketNumber() throws Exception {
        seed("SF-2026-000104", requesterId, "Wobbly chair at desk 12",
                "The chair at desk 12 wobbles badly and is unsafe to sit on.",
                MaintenanceCategory.HVAC, UrgencyLevel.LOW);

        mvc.perform(get("/admin/requests/lookup")
                        .param("ticketNumber", "SF-2026-000104")
                        .session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Request summary")))
                .andExpect(content().string(containsString("Wobbly chair at desk 12")))
                .andExpect(content().string(containsString("Status history")))
                .andExpect(content().string(containsString("Clear result")))
                // The typed ticket number is handed back to the form.
                .andExpect(content().string(containsString("value=\"SF-2026-000104\"")));
    }

    @Test
    void accountListRendersItsRowsAndTheirPerRowControls() throws Exception {
        mvc.perform(get("/admin/users").session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User management")))
                .andExpect(content().string(containsString("alice")))
                .andExpect(content().string(containsString("ACTIVE")))
                // A role reads as a word wherever the page shows one - the badge and
                // the per-row <select> label - while the option's value stays the bare
                // enum, because that is what the controller binds.
                .andExpect(content().string(containsString("value=\"REQUESTER\"")))
                .andExpect(content().string(containsString(">Requester</option>")))
                .andExpect(content().string(containsString("badge--brand\">Requester<")))
                // The control ids are suffixed per row, so a label never points at
                // an id that appears more than once.
                .andExpect(content().string(containsString("id=\"role-" + requesterId + "\"")))
                .andExpect(content().string(containsString("id=\"role-" + administratorId + "\"")))
                .andExpect(content().string(containsString(
                        "id=\"account-status-" + requesterId + "\"")))
                // Per-row forms keep their routes and their field names.
                .andExpect(content().string(containsString(
                        "action=\"/admin/users/" + requesterId + "/role\"")))
                .andExpect(content().string(containsString(
                        "action=\"/admin/users/" + requesterId + "/status\"")))
                .andExpect(content().string(containsString("name=\"role\"")))
                .andExpect(content().string(containsString("name=\"accountStatus\"")))
                // The option value stays the enum name the controller binds. The
                // label is the bare enum too: a select sizes to its widest option,
                // and a sentence there widened this column enough to push the table
                // past the content width. What the choice does is stated in the card
                // footer instead, and tied to the control by aria-describedby.
                .andExpect(content().string(containsString("value=\"DISABLED\"")))
                .andExpect(content().string(containsString(
                        "aria-describedby=\"account-status-note\"")))
                .andExpect(content().string(containsString("id=\"account-status-note\"")))
                // No credential material of any kind reaches the page.
                .andExpect(content().string(not(containsString("$2"))))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    private MaintenanceRequest seed(String ticketNumber,
                                    Long ownerId,
                                    String title,
                                    String description,
                                    MaintenanceCategory category,
                                    UrgencyLevel urgency) {
        Long locationId = locations.findByLocationCode("COM1-L1")
                .orElseGet(() -> locations.save(new Location(
                        "COM1-L1", "COM1", "1", "Lobby", "COM1 Level 1 Lobby", true)))
                .getId();

        Instant submittedAt = Instant.parse("2026-09-20T02:30:00Z");
        MaintenanceRequest request = requests.saveAndFlush(MaintenanceRequest.submit(
                ticketNumber, ownerId, locationId, title, description, category, urgency,
                submittedAt));
        history.saveAndFlush(RequestStatusHistory.initialSubmission(
                request.getId(), ownerId, submittedAt));
        return request;
    }

    private Long createAccount(String username, Role role) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username + " (test)");
        command.setPassword(PASSWORD);
        command.setRole(role);
        return users.createUser(command, null);
    }

    private MockHttpSession login(String username) throws Exception {
        // A failed sign-in redirects to /login?error, so the expected redirect is
        // asserted here: a session that never authenticated must not be mistaken
        // for one that did and quietly turn every assertion below into a 302 check.
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
