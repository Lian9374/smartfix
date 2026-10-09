package com.smartfix.auth;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real role routing, rendered pages, account creation and enforced password setup. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:role-workspaces;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class RoleWorkspacesIT {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired AccountAdministrationService administration;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.smartfix.request.service.RequestQueryService requests;
    Long admin, requester;
    final String password="TestPassword9";
    @BeforeEach void fixtures() {
        admin=users.createUser(command("workspace.admin",Role.ADMINISTRATOR),null);
        requester=users.createUser(command("workspace.requester",Role.REQUESTER),admin);
        users.createUser(command("workspace.tech",Role.TECHNICIAN),admin);
    }
    CreateUserCommand command(String name, Role role) {
        var c=new CreateUserCommand(); c.setUsername(name); c.setDisplayName(name); c.setPassword(password); c.setRole(role); return c;
    }
    RequestPostProcessor account(String name) { return user(new SmartFixUserDetails(users.findAuthenticationByUsername(name))); }
    @ParameterizedTest @ValueSource(strings={"/admin","/admin/users","/admin/technicians","/admin/facilities","/admin/requests"})
    void adminPagesRejectOtherRoles(String route) throws Exception {
        mvc.perform(get(route).with(account("workspace.requester"))).andExpect(status().isForbidden());
        mvc.perform(get(route).with(account("workspace.tech"))).andExpect(status().isForbidden());
        mvc.perform(get(route)).andExpect(status().is3xxRedirection());
    }
    @Test void privilegedServiceRejectsForgedActor() {
        assertThatThrownBy(()->administration.createUser(command("forged.admin",Role.ADMINISTRATOR),requester)).isInstanceOf(AccessDeniedException.class);
        assertThat(users.listUsers()).noneMatch(u -> u.username().equals("forged.admin"));
    }
    @Test void routesAndRealEmptyWorkspacesRender() throws Exception {
        mvc.perform(get("/").with(account("workspace.admin"))).andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/").with(account("workspace.tech"))).andExpect(redirectedUrl("/technician"));
        for(String route:new String[]{"/admin","/admin/users","/admin/technicians","/admin/requests"})
            mvc.perform(get(route).with(account("workspace.admin"))).andExpect(status().isOk());
        mvc.perform(get("/technician").with(account("workspace.tech"))).andExpect(status().isOk()).andExpect(content().string(not(containsString("href=\"/admin/users\""))));
    }
    @Test void managedEngineerMustChangePasswordBeforeUsingWorkspace() throws Exception {
        Long id=administration.createUser(command("managed.tech",Role.TECHNICIAN),admin);
        assertThat(users.getUserAccess(id).passwordChangeRequired()).isTrue();
        mvc.perform(get("/technician").with(account("managed.tech"))).andExpect(redirectedUrl("/account/password"));
        mvc.perform(post("/technician/profile").with(account("managed.tech")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/account/password").with(account("managed.tech"))).andExpect(status().isOk());
        mvc.perform(post("/account/password").with(account("managed.tech")).with(csrf())
                .param("currentPassword",password).param("newPassword","ChangedPassword8").param("confirmPassword","DifferentPassword8"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("value=\"ChangedPassword8\""))));
        mvc.perform(post("/account/password").with(account("managed.tech")).with(csrf())
                .param("currentPassword",password).param("newPassword","ChangedPassword8").param("confirmPassword","ChangedPassword8"))
                .andExpect(redirectedUrl("/login?passwordChanged"));
        assertThat(users.getUserAccess(id).passwordChangeRequired()).isFalse();
        mvc.perform(get("/technician").with(account("managed.tech"))).andExpect(status().isOk());
    }
    @Test void resetRequiresAdministratorPasswordAndForcesReplacement() throws Exception {
        Long target=users.findAuthenticationByUsername("workspace.tech").userId();
        assertThatThrownBy(()->administration.resetPassword(target,admin,"wrong", "ReplacementPassword8","ReplacementPassword8"))
                .isInstanceOf(com.smartfix.common.exception.InputValidationException.class);
        administration.resetPassword(target,admin,password,"ReplacementPassword8","ReplacementPassword8");
        assertThat(users.getUserAccess(target).passwordChangeRequired()).isTrue();
        mvc.perform(get("/technician").with(account("workspace.tech"))).andExpect(redirectedUrl("/account/password"));
    }
    @Test void administratorSearchUsesFinalPriorityAndTreatsWildcardAsLiteral() {
        jdbc.update("INSERT INTO locations(id,location_code,display_name,active) VALUES(50,'SEARCH-QA','Search QA Lab',true)");
        jdbc.update("INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,category,urgency_level,final_urgency_level,status,version,created_at,updated_at) VALUES(50,'SF-2026-000050',?,50,'100% fixture','Synthetic issue','ELECTRICAL','LOW','HIGH','UNDER_REVIEW',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",requester);
        jdbc.update("INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,category,urgency_level,status,version,created_at,updated_at) VALUES(51,'SF-2026-000051',?,50,'100 normal fixture','Synthetic issue','ELECTRICAL','HIGH','SUBMITTED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",requester);
        var page=requests.searchForAdministration(admin,"100%",null,null,com.smartfix.request.domain.UrgencyLevel.HIGH,"oldest",0,20);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(com.smartfix.request.dto.AdminRequestRow::id).containsExactly(50L);
        assertThat(requests.searchForAdministration(admin,null,com.smartfix.request.domain.RequestStatus.SUBMITTED,null,null,"newest",0,20).getTotalElements()).isEqualTo(1);
    }
}
