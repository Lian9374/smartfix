package com.smartfix.facility;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.facility.domain.Facility;
import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.facility.domain.Location;
import com.smartfix.facility.dto.CampusMapFacilityResponse;
import com.smartfix.facility.dto.CampusMapResponse;
import com.smartfix.facility.repository.FacilityRepository;
import com.smartfix.facility.repository.LocationRepository;
import com.smartfix.facility.service.CampusMapService;
import com.smartfix.request.dto.SubmitMaintenanceRequestCommand;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.request.repository.RequestStatusHistoryRepository;
import com.smartfix.request.service.RequestTicketNumberGenerator;
import com.smartfix.user.domain.Role;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.dto.ChangeAccountStatusCommand;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import com.smartfix.reporting.service.CampusMapStatusService;
import com.smartfix.reporting.dto.CampusMapSnapshot;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.UrgencyLevel;

import static com.smartfix.facility.domain.FacilityStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login, map queries, security and templates; only the unrelated PostgreSQL ticket counter is stubbed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-campus-map-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampusMapIntegrationIT {
    private static final String PASSWORD = "TestPassword9";
    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private CampusMapService maps;
    @Autowired private FacilityRepository facilities;
    @Autowired private LocationRepository locations;
    @Autowired private MaintenanceRequestRepository requests;
    @Autowired private RequestStatusHistoryRepository history;
    @Autowired private CampusMapStatusService statuses;
    // H2 cannot execute the counter's PostgreSQL ON CONFLICT statement. The counter itself
    // remains covered against PostgreSQL in RequestWorkflowPostgresIT; submission is real here.
    @MockitoBean private RequestTicketNumberGenerator tickets;
    private Long requesterId, administratorId, technicianId;
    private Location engineering, library, inactive;
    private Facility projector;

    @BeforeAll void accounts() {
        requesterId = account("map.requester", Role.REQUESTER);
        administratorId = account("map.admin", Role.ADMINISTRATOR);
        technicianId = account("map.technician", Role.TECHNICIAN);
    }

    @BeforeEach void campus() {
        when(tickets.nextTicketNumber()).thenReturn("SF-2026-900001");
        history.deleteAll();
        requests.deleteAll();
        facilities.deleteAll();
        facilities.flush();
        locations.deleteAll();
        locations.flush();
        engineering = location("ENG-L1", "Engineering", "Level 1", "ROOM_SECRET_A", true);
        library = location("LIB-L2", "Library", "Level 2", "ROOM_SECRET_B", true);
        inactive = location("OLD-L1", "Closed building", "Level 1", "ROOM_SECRET_C", false);
        projector = facility(engineering, "Lecture projector", OPERATIONAL);
        facility(engineering, "Drinking fountain", UNDER_MAINTENANCE);
        facility(library, "Reading room lighting", OUT_OF_SERVICE);
        facility(inactive, "INACTIVE_FACILITY", OPERATIONAL);
    }

    @Test void anonymousUsersMustSignIn() throws Exception {
        mvc.perform(get("/campus-map")).andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test void allRolesCanUseTheMapWithServerFilteredResponses() throws Exception {
        for (String username : List.of("map.requester", "map.technician", "map.admin")) {
            String html = mvc.perform(get("/campus-map").session(login(username)))
                    .andExpect(status().isOk()).andExpect(view().name("facility/map"))
                    .andExpect(content().string(containsString("Lecture projector")))
                    .andExpect(content().string(not(containsString("INACTIVE_FACILITY"))))
                    .andExpect(content().string(not(containsString("Closed building"))))
                    .andExpect(content().string(not(containsString("PRIVATE_INTERNAL_NOTE"))))
                    .andReturn().getResponse().getContentAsString();
            assertEquals(username.equals("map.admin"), html.contains("ROOM_SECRET_A"));
            assertEquals(username.equals("map.admin"), html.contains("Manage facilities"));
            assertEquals(username.equals("map.requester"), html.contains("/requests/new?locationId="));
            export(username.equals("map.admin") ? "map-admin.html" :
                    username.equals("map.requester") ? "map-requester.html" : "map-technician.html", html);
        }
        for (Long actor : List.of(requesterId, technicianId)) {
            var map = browse(actor);
            assertTrue(map.facilities().stream().allMatch(row -> row.room() == null));
            assertTrue(map.facilities().stream().noneMatch(row -> row.locationDisplayName().contains("SECRET")));
        }
    }

    @Test void restrictedRoomsCannotBeDiscoveredThroughSearch() {
        for (Long actor : List.of(requesterId, technicianId)) {
            assertEquals(0, maps.browse(actor, "ROOM_SECRET_A", "", "", null, 0, 12)
                    .facilities().getTotalElements());
        }
        assertEquals(2, maps.browse(administratorId, "ROOM_SECRET_A", "", "", null, 0, 12)
                .facilities().getTotalElements());
    }

    @Test void directoryAndStatusCountsComeFromActiveData() {
        var map = browse(requesterId);
        assertEquals(2, map.activeLocationCount());
        assertEquals(3, map.facilityCount());
        assertEquals(List.of("Engineering", "Library"), map.buildings().stream().map(b -> b.name()).toList());
        assertEquals(2, map.buildings().getFirst().facilityCount());
        assertEquals(List.of("Level 1", "Level 2"), map.floors());
        assertEquals(1, map.getOperationalCount());
        assertEquals(1, map.getMaintenanceCount());
        assertEquals(1, map.getOutOfServiceCount());
    }

    @Test void buildingFloorStatusAndSearchCanBeCombined() throws Exception {
        var map = maps.browse(requesterId, "  DRINKING  ", " Engineering ", " Level 1 ",
                UNDER_MAINTENANCE, 0, 12);
        assertEquals(List.of("Drinking fountain"), map.facilities().stream().map(CampusMapFacilityResponse::name).toList());
        assertEquals(List.of("Level 1"), map.floors());
        assertEquals(1, map.getMaintenanceCount());
        String html = mvc.perform(get("/campus-map").session(login("map.requester"))
                        .param("building", "Engineering").param("floor", "Level 1")
                        .param("status", "UNDER_MAINTENANCE").param("q", "drinking"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Drinking fountain")))
                .andExpect(content().string(not(containsString("Lecture projector"))))
                .andReturn().getResponse().getContentAsString();
        export("map-filtered.html", html);
    }

    @Test void statusCountsDescribeTheSelectedAreaBeforeTheStatusFilter() {
        var map = maps.browse(requesterId, "", "Engineering", "", OPERATIONAL, 0, 12);
        assertEquals(1, map.facilities().getTotalElements());
        assertEquals(1, map.getOperationalCount());
        assertEquals(1, map.getMaintenanceCount());
        assertEquals(0, map.getOutOfServiceCount());
        assertEquals(0, maps.browse(requesterId, "", "Library", "Level 1", null, 0, 12)
                .facilities().getTotalElements());
    }

    @Test void searchTreatsWildcardCharactersAsLiteralText() {
        assertEquals(0, maps.browse(requesterId, "%", "", "", null, 0, 12).facilities().getTotalElements());
        assertEquals(0, maps.browse(requesterId, "_", "", "", null, 0, 12).facilities().getTotalElements());
    }

    @Test void emptyBuildingsAreRealLocationsAndNotInventedFacilities() throws Exception {
        location("ART-L1", "Arts", "Level 1", "ROOM_SECRET_D", true);
        assertEquals(0, browse(requesterId).buildings().getFirst().facilityCount());
        mvc.perform(get("/campus-map").session(login("map.requester")).param("building", "Arts"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No facilities match")))
                .andExpect(content().string(containsString("Clear filters")));
    }

    @Test void emptyMapHasAnHonestUsefulState() throws Exception {
        facilities.deleteAll();
        String html = mvc.perform(get("/campus-map").session(login("map.requester")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No facilities added yet")))
                .andExpect(content().string(not(containsString("Lecture projector"))))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, browse(requesterId).facilityCount());
        assertEquals(2, browse(requesterId).buildings().size());
        export("map-empty.html", html);
        locations.deleteAll();
        mvc.perform(get("/campus-map").session(login("map.requester")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Buildings will appear")));
    }

    @Test void paginationIsStableBoundedAndKeepsTheFilters() throws Exception {
        for (int i = 0; i < 25; i++) facility(engineering, "Panel " + String.format("%02d", i), OPERATIONAL);
        var first = maps.browse(requesterId, "Panel", "Engineering", "Level 1", OPERATIONAL, -1, 12);
        var last = maps.browse(requesterId, "Panel", "Engineering", "Level 1", OPERATIONAL, Integer.MAX_VALUE, 12);
        assertEquals(0, first.facilities().getNumber());
        assertEquals(12, first.facilities().getNumberOfElements());
        assertEquals(2, last.facilities().getNumber());
        assertEquals("Panel 24", last.facilities().getContent().getFirst().name());
        assertEquals(48, maps.browse(requesterId, "", "", "", null, 0, Integer.MAX_VALUE).facilities().getSize());
        assertEquals(12, maps.browse(requesterId, "", "", "", null, 0, 0).facilities().getSize());
        mvc.perform(get("/campus-map").session(login("map.requester"))
                        .param("q", "Panel").param("building", "Engineering").param("floor", "Level 1")
                        .param("status", "OPERATIONAL").param("page", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Page 2 of 3")))
                .andExpect(content().string(containsString("page=0")))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(containsString("floor=Level%201")))
                .andExpect(content().string(containsString("status=OPERATIONAL")));
    }

    @Test void filterValidationRejectsMalformedOrOversizedInput() throws Exception {
        MockHttpSession session = login("map.requester");
        mvc.perform(get("/campus-map").session(session).param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/campus-map").session(session).param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
        assertThrows(InputValidationException.class,
                () -> maps.browse(requesterId, "", "x".repeat(101), "", null, 0, 12));
        assertThrows(InputValidationException.class,
                () -> maps.browse(requesterId, "", "", "x".repeat(21), null, 0, 12));
    }

    @Test void theServiceAlsoRequiresAnExistingActiveActor() {
        assertThrows(AccessDeniedException.class, () -> browse(null));
        assertThrows(AccessDeniedException.class, () -> browse(Long.MAX_VALUE));
    }

    @Test void disablingAnAccountRevokesItsMapAccessIncludingTheExistingSession() throws Exception {
        Long disabled = account("map.disabled", Role.REQUESTER);
        MockHttpSession session = login("map.disabled");
        var command = new ChangeAccountStatusCommand();
        command.setAccountStatus(AccountStatus.DISABLED);
        users.changeAccountStatus(disabled, command, administratorId);
        assertThrows(AccessDeniedException.class, () -> browse(disabled));
        mvc.perform(get("/campus-map").session(session)).andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test void homeAndNavigationExposeTheActualMapEntry() throws Exception {
        for (String username : List.of("map.requester", "map.technician", "map.admin")) {
            String route = username.equals("map.requester") ? "/" : username.equals("map.admin") ? "/admin" : "/technician";
            if (!route.equals("/")) mvc.perform(get("/").session(login(username))).andExpect(redirectedUrl(route));
            String home = mvc.perform(get(route).session(login(username)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/campus-map\"")))
                    .andReturn().getResponse().getContentAsString();
            if (username.equals("map.requester")) export("home.html", home);
        }
        mvc.perform(get("/campus-map").session(login("map.requester")))
                .andExpect(status().isOk()).andExpect(content().string(containsString(
                        "href=\"/campus-map\" aria-current=\"page\"")));
    }

    @Test void facilityLinkPreselectsAnActiveLocationWithoutWritingAnything() throws Exception {
        long before = requests.count();
        var result = mvc.perform(get("/requests/new").session(login("map.requester"))
                        .param("locationId", engineering.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        var command = (SubmitMaintenanceRequestCommand) result.getModelAndView().getModel().get("command");
        assertEquals(engineering.getId(), command.getLocationId());
        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("value=\"" + engineering.getId() + "\" selected=\"selected\""));
        assertEquals(before, requests.count());
        assertEquals(OPERATIONAL, facilities.findById(projector.getId()).orElseThrow().getStatus());
        export("request-prefilled.html", html);
    }

    @Test void invalidInactiveAndUnauthorizedPrefillIsRejected() throws Exception {
        MockHttpSession requester = login("map.requester");
        for (Long id : List.of(inactive.getId(), Long.MAX_VALUE)) {
            mvc.perform(get("/requests/new").session(requester).param("locationId", id.toString()))
                    .andExpect(status().isBadRequest());
        }
        for (String username : List.of("map.technician", "map.admin")) {
            mvc.perform(get("/requests/new").session(login(username))
                            .param("locationId", engineering.getId().toString()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void administratorManualStatusChangesAppearOnTheMap() throws Exception {
        mvc.perform(post("/admin/facilities/" + projector.getId() + "/status")
                        .session(login("map.admin")).with(csrf()).param("status", "OUT_OF_SERVICE"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/facilities"));
        var map = browse(requesterId);
        assertEquals(0, map.getOperationalCount());
        assertEquals(2, map.getOutOfServiceCount());
        assertEquals(OUT_OF_SERVICE, map.facilities().stream().filter(row -> row.id().equals(projector.getId()))
                .findFirst().orElseThrow().status());
        mvc.perform(post("/admin/facilities/" + projector.getId() + "/status")
                        .session(login("map.requester")).with(csrf()).param("status", "OPERATIONAL"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/facilities/" + projector.getId() + "/status")
                        .session(login("map.admin")).param("status", "OPERATIONAL"))
                .andExpect(status().isForbidden());
    }

    @Test void submittingFromTheMapDoesNotAutomaticallyChangeFacilityStatus() throws Exception {
        mvc.perform(post("/requests").session(login("map.requester")).with(csrf())
                        .param("locationId", engineering.getId().toString())
                        .param("title", "Projector flickers during lectures")
                        .param("description", "The image flickers after switching the projector on.")
                        .param("category", "ELECTRICAL").param("urgencyLevel", "MEDIUM"))
                .andExpect(status().isFound());
        assertEquals(1, requests.count());
        assertEquals(OPERATIONAL, facilities.findById(projector.getId()).orElseThrow().getStatus());
    }

    @Test void onlineMapUsesVerifiedRealPlacesAndDoesNotInventStatus() throws Exception {
        var snapshot = statuses.snapshot(requesterId);
        assertTrue(snapshot.buildings().size() > 150);
        assertEquals(4, snapshot.buildings().stream().map(b -> b.campus()).distinct().count());
        var com3 = snapshot.buildings().stream().filter(b -> b.name().equals("COM3")).findFirst().orElseThrow();
        assertEquals(1.294640832761884, com3.latitude(), 0.000001);
        assertEquals(103.7745659764451, com3.longitude(), 0.000001);
        assertTrue(snapshot.buildings().stream().allMatch(b -> !b.hasActivity() && b.operationalFacilities() == 0));
        assertEquals(0, snapshot.unmappedRequests());
        assertEquals(3, snapshot.unmappedFacilities());
        mvc.perform(get("/campus-map").session(login("map.requester")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("nus-online-map")))
                .andExpect(content().string(containsString("COM3")))
                .andExpect(content().string(containsString("OneMap")));
    }

    @Test void mapCountsFollowActualRequestLifecycleAndKeepFacilityStatusesIndependent() throws Exception {
        var com1 = location("COM1-L2", "com-1", "Level 2", "ROOM_MAP_SECRET", true);
        var secondRoom = location("COM1-L3", "School of Computing", "Level 3", "ROOM_OTHER_SECRET", true);
        facility(com1, "PRIVATE_PROJECTOR_NAME", UNDER_MAINTENANCE);
        var report = requests.saveAndFlush(MaintenanceRequest.submit("SF-2026-900002", requesterId,
                com1.getId(), "PRIVATE_TICKET_TITLE", "PRIVATE_TICKET_DESCRIPTION", MaintenanceCategory.ELECTRICAL,
                UrgencyLevel.HIGH, Instant.now()));
        requests.saveAndFlush(MaintenanceRequest.submit("SF-2026-900003", requesterId, secondRoom.getId(),
                "PRIVATE_SECOND_TITLE", "PRIVATE_SECOND_DESCRIPTION", MaintenanceCategory.ELECTRICAL,
                UrgencyLevel.LOW, Instant.now()));
        assertEquals(2, point("COM1").faultReports());
        assertEquals(1, point("COM1").maintenanceFacilities());
        for (var transition : List.of(RequestStatus.UNDER_REVIEW, RequestStatus.ASSIGNED)) {
            report.transitionTo(transition, Instant.now()); report = requests.saveAndFlush(report);
            assertEquals(2, point("COM1").faultReports());
            assertEquals(0, point("COM1").repairs());
        }
        report.transitionTo(RequestStatus.IN_PROGRESS, Instant.now()); report = requests.saveAndFlush(report);
        assertEquals(1, point("COM1").faultReports());
        assertEquals(1, point("COM1").repairs());
        String html = mvc.perform(get("/campus-map").session(login("map.requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        export("map-live.html", html);
        var session = login("map.requester");
        for (var transition : List.of(RequestStatus.RESOLVED, RequestStatus.CONFIRMED, RequestStatus.REOPENED)) {
            report.transitionTo(transition, Instant.now()); report = requests.saveAndFlush(report);
            long count = transition == RequestStatus.REOPENED ? 2 : 1;
            var response = mvc.perform(get("/campus-map/status").session(session))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("PRIVATE_"));
            assertFalse(response.contains("ROOM_MAP_SECRET"));
            assertFalse(response.contains("locationId"));
            assertFalse(response.contains("requesterId"));
            assertFalse(response.contains("ticketNumber"));
            assertEquals(count, point("COM1").faultReports());
            assertEquals(0, point("COM1").repairs());
        }
        assertEquals(1, point("COM1").maintenanceFacilities());
    }

    @Test void unmappedAndInactiveLocationsAreCountedWithoutInventingCoordinates() {
        for (var address : List.of(engineering, inactive)) {
            requests.saveAndFlush(MaintenanceRequest.submit("SF-2026-" + (900100 + address.getId()),
                    requesterId, address.getId(), "PRIVATE_UNMAPPED", "PRIVATE_UNMAPPED_DETAILS",
                    MaintenanceCategory.ELECTRICAL, UrgencyLevel.LOW, Instant.now()));
        }
        var snapshot = statuses.snapshot(requesterId);
        assertEquals(2, snapshot.unmappedRequests());
        assertEquals(0, snapshot.getFaultBuildings());
        assertTrue(snapshot.buildings().stream().noneMatch(b -> b.name().equals("Engineering") || b.name().equals("Closed building")));
    }

    @Test void statusEndpointIsReadOnlyAuthenticatedAndHasTheSameFieldBoundaryForAllRoles() throws Exception {
        mvc.perform(get("/campus-map/status")).andExpect(status().isFound());
        for (String username : List.of("map.requester", "map.technician", "map.admin")) {
            var session = login(username);
            String json = mvc.perform(get("/campus-map/status").session(session))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(json.contains("SECRET"));
            assertFalse(json.contains("PRIVATE"));
            mvc.perform(post("/campus-map/status").session(session).with(csrf())).andExpect(status().isForbidden());
        }
        assertThrows(AccessDeniedException.class, () -> statuses.snapshot(null));
        assertThrows(AccessDeniedException.class, () -> statuses.snapshot(Long.MAX_VALUE));
        assertEquals(0, requests.count());
    }

    private CampusMapSnapshot.Building point(String name) {
        return statuses.snapshot(requesterId).buildings().stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow();
    }

    private CampusMapResponse browse(Long actor) { return maps.browse(actor, "", "", "", null, 0, 12); }
    private Location location(String code, String building, String floor, String room, boolean active) {
        return locations.saveAndFlush(new Location(code, building, floor, room,
                building + " " + floor + " " + room, active));
    }
    private Facility facility(Location location, String name, FacilityStatus status) {
        return facilities.saveAndFlush(new Facility(location, name, "PRIVATE_INTERNAL_NOTE", status,
                OffsetDateTime.parse("2026-10-01T10:00:00Z")));
    }
    private Long account(String username, Role role) {
        var command = new CreateUserCommand();
        command.setUsername(username); command.setDisplayName(username);
        command.setPassword(PASSWORD); command.setRole(role);
        return users.createUser(command, null);
    }
    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
    private void export(String filename, String html) throws Exception {
        String directory = System.getProperty("smartfix.map.ui.export");
        if (directory != null) {
            Path output = Path.of(directory); Files.createDirectories(output);
            Files.writeString(output.resolve(filename), html);
        }
    }
}
