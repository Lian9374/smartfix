package com.smartfix.technician;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.ChangeAccountStatusCommand;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, filters, service, JPA and Thymeleaf against an isolated H2 schema. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:technician-profile-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = {"/db/auth-test-schema.sql", "/db/migration/V3__create_locations.sql",
        "/db/technician-test-schema.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TechnicianProfileIT {
    private static final String ROUTE = "/technician/profile";
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired TechnicianDirectoryService directory;
    @Autowired JdbcTemplate jdbc;
    private Long technicianId;
    private Long otherTechnicianId;

    @BeforeEach
    void fixtures() {
        technicianId = createUser("tech.one", Role.TECHNICIAN);
        otherTechnicianId = createUser("tech.two", Role.TECHNICIAN);
        createUser("requester", Role.REQUESTER);
        createUser("administrator", Role.ADMINISTRATOR);
        jdbc.update("INSERT INTO locations (id, location_code, display_name) VALUES (10, 'LIB', 'Library'), (20, 'LAB', 'Lab')");
    }

    @Test
    void firstVisitRendersAnAccessibleFormWithoutCreatingAProfile() throws Exception {
        mvc.perform(get(ROUTE).with(account("tech.one")))
                .andExpect(status().isOk())
                .andExpect(htmlMatches("<h1\\b", 1))
                .andExpect(htmlMatches("<legend\\b", 3))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(htmlMatches("<input\\b[^>]*name=\"skills\"", 5))
                .andExpect(htmlMatches("<input\\b[^>]*name=\"serviceAreaIds\"", 2))
                .andExpect(htmlMatches("<input\\b[^>]*name=\"availabilityStatus\"", 3))
                .andExpect(htmlMatches("<label\\b[^>]*>[^<\\s][^<]*</label>", 10))
                .andExpect(content().string(containsString("Heating, ventilation and air conditioning")))
                .andExpect(content().string(containsString("Library")))
                .andDo(result -> savePreview("profile", result.getResponse().getContentAsString()));
        assertThat(directory.getProfile(technicianId)).isEmpty();
    }

    @Test
    void savesThenRendersSelectionsAndReplacesPreferences() throws Exception {
        mvc.perform(validPost("tech.one"))
                .andExpect(status().isFound()).andExpect(redirectedUrl(ROUTE))
                .andExpect(flash().attribute("successMessage", "Your technician profile was saved."));
        var first = directory.getProfile(technicianId).orElseThrow();
        mvc.perform(get(ROUTE).with(account("tech.one")))
                .andExpect(status().isOk())
                .andExpect(checkedInput("skills", "ELECTRICAL"))
                .andExpect(checkedInput("serviceAreaIds", "10"))
                .andExpect(checkedInput("availabilityStatus", "AVAILABLE"))
                .andDo(result -> savePreview("saved-profile", result.getResponse().getContentAsString()));
        mvc.perform(post(ROUTE).with(account("tech.one")).with(csrf())
                .param("skills", "PLUMBING", "HVAC").param("serviceAreaIds", "20")
                .param("availabilityStatus", "ON_LEAVE").param("version", Long.toString(first.version())))
                .andExpect(status().isFound());
        var updated = directory.getProfile(technicianId).orElseThrow();
        assertThat(updated.profileId()).isEqualTo(first.profileId());
        assertThat(updated.skills()).containsExactlyInAnyOrder(MaintenanceCategory.PLUMBING, MaintenanceCategory.HVAC);
        assertThat(updated.serviceAreaIds()).containsExactly(20L);
        assertThat(updated.availabilityStatus()).isEqualTo(AvailabilityStatus.ON_LEAVE);
        assertThat(updated.version()).isGreaterThan(first.version());
    }

    @Test
    void formIdsCannotSelectAnotherTechnicianOrChangeTheActiveFlag() throws Exception {
        var other = directory.updateProfile(otherTechnicianId, preferences(MaintenanceCategory.PLUMBING));
        mvc.perform(validPost("tech.one").param("userId", otherTechnicianId.toString())
                .param("profileId", other.profileId().toString()).param("active", "false"))
                .andExpect(status().isFound());
        assertThat(directory.getProfile(technicianId).orElseThrow().active()).isTrue();
        assertThat(directory.getProfile(technicianId).orElseThrow().skills()).containsExactly(MaintenanceCategory.ELECTRICAL);
        assertThat(directory.getProfile(otherTechnicianId).orElseThrow().skills()).containsExactly(MaintenanceCategory.PLUMBING);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"REQUESTER", "ADMINISTRATOR"})
    void otherRolesCannotReadOrWriteProfiles(Role role) throws Exception {
        String username = role.name().toLowerCase(java.util.Locale.ROOT);
        mvc.perform(get(ROUTE).with(account(username))).andExpect(status().isForbidden());
        mvc.perform(validPost(username)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM technician_profiles", Long.class)).isZero();
    }

    @Test
    void anonymousAndCsrfLessRequestsCannotSave() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(post(ROUTE).with(csrf())).andExpect(status().isFound());
        mvc.perform(post(ROUTE).with(account("tech.one"))).andExpect(status().isForbidden());
        mvc.perform(put(ROUTE).with(account("tech.one")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void missingSelectionsReturnFieldErrorsWithoutWriting() throws Exception {
        mvc.perform(post(ROUTE).with(account("tech.one")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("profileCommand", "skills", "serviceAreaIds", "availabilityStatus"))
                .andExpect(content().string(containsString("Please check your selections")))
                .andDo(result -> savePreview("errors", result.getResponse().getContentAsString()));
        assertThat(directory.getProfile(technicianId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"skills", "serviceAreaIds", "availabilityStatus"})
    void malformedFormValuesReturn400(String field) throws Exception {
        mvc.perform(post(ROUTE).with(account("tech.one")).with(csrf())
                .param("skills", field.equals("skills") ? "INVALID" : "ELECTRICAL")
                .param("serviceAreaIds", field.equals("serviceAreaIds") ? "INVALID" : "10")
                .param("availabilityStatus", field.equals("availabilityStatus") ? "INVALID" : "AVAILABLE"))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("profileCommand", field));
        assertThat(directory.getProfile(technicianId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {999L, 20L})
    void unknownOrInactiveAreasAreRejectedOnTheServer(long areaId) throws Exception {
        jdbc.update("UPDATE locations SET active = FALSE WHERE id = 20");
        mvc.perform(validPost("tech.one").param("serviceAreaIds", Long.toString(areaId)))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("active service areas")));
        assertThat(directory.getProfile(technicianId)).isEmpty();
    }

    @Test
    void staleFormCannotOverwriteANewerSave() throws Exception {
        var original = directory.updateProfile(technicianId, preferences(MaintenanceCategory.ELECTRICAL));
        var update = preferences(MaintenanceCategory.PLUMBING);
        update.setVersion(original.version());
        directory.updateProfile(technicianId, update);
        mvc.perform(validPost("tech.one").param("version", Long.toString(original.version())))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Reload the page")));
        assertThat(directory.getProfile(technicianId).orElseThrow().skills()).containsExactly(MaintenanceCategory.PLUMBING);
    }

    @Test
    void disabledAccountLosesAccessOnTheNextRequest() throws Exception {
        RequestPostProcessor sessionAccount = account("tech.one");
        var change = new ChangeAccountStatusCommand();
        change.setAccountStatus(AccountStatus.DISABLED);
        users.changeAccountStatus(technicianId, change, null);
        mvc.perform(validPost("tech.one").with(sessionAccount)).andExpect(status().isFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM technician_profiles", Long.class)).isZero();
    }

    @Test
    void directoryReflectsAccountAndPreferenceChanges() {
        var profile = directory.updateProfile(technicianId, preferences(MaintenanceCategory.ELECTRICAL));
        directory.updateProfile(otherTechnicianId, preferences(MaintenanceCategory.PLUMBING));
        assertThat(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 10L))
                .extracting(candidate -> candidate.userId()).containsExactly(technicianId);
        var leave = preferences(MaintenanceCategory.ELECTRICAL);
        leave.setVersion(profile.version());
        leave.setAvailabilityStatus(AvailabilityStatus.ON_LEAVE);
        directory.updateProfile(technicianId, leave);
        assertThat(directory.findCandidates(MaintenanceCategory.ELECTRICAL, 10L)).isEmpty();
    }

    @Test
    void emptyLocationsDisableSaveAndLocationNamesAreEscaped() throws Exception {
        jdbc.update("UPDATE locations SET display_name = '<script>alert(1)</script>' WHERE id = 10");
        mvc.perform(get(ROUTE).with(account("tech.one"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        jdbc.update("UPDATE locations SET active = FALSE");
        mvc.perform(get(ROUTE).with(account("tech.one"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("No active locations")))
                .andExpect(htmlMatches("<button\\b[^>]*disabled[^>]*>Save profile</button>", 1));
    }

    private static void savePreview(String name, String html) throws java.io.IOException {
        Path directory = Files.createDirectories(Path.of("target", "technician-preview"));
        Files.writeString(directory.resolve(name + ".html"), html);
    }

    // The response is HTML5, not XML; XPath's XML parser rejects HTML named entities and void tags.
    private static ResultMatcher htmlMatches(String expression, long expectedCount) {
        return result -> assertThat(Pattern.compile(expression)
                .matcher(result.getResponse().getContentAsString()).results().count()).isEqualTo(expectedCount);
    }

    private static ResultMatcher checkedInput(String name, String value) {
        return htmlMatches("<input\\b(?=[^>]*\\bname=\"" + Pattern.quote(name)
                + "\")(?=[^>]*\\bvalue=\"" + Pattern.quote(value) + "\")(?=[^>]*\\bchecked)[^>]*>", 1);
    }

    private MockHttpServletRequestBuilder validPost(String username) {
        return post(ROUTE).with(account(username)).with(csrf()).param("skills", "ELECTRICAL")
                .param("serviceAreaIds", "10").param("availabilityStatus", "AVAILABLE");
    }

    private RequestPostProcessor account(String username) {
        return user(new SmartFixUserDetails(users.findAuthenticationByUsername(username)));
    }

    private Long createUser(String username, Role role) {
        var command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username);
        command.setPassword("TestPassword9");
        command.setRole(role);
        return users.createUser(command, null);
    }

    private static UpdateTechnicianProfileCommand preferences(MaintenanceCategory skill) {
        var command = new UpdateTechnicianProfileCommand();
        command.setSkills(Set.of(skill));
        command.setServiceAreaIds(Set.of(10L));
        command.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return command;
    }
}
