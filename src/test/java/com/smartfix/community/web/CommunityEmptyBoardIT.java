package com.smartfix.community.web;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The board with nothing on it at all.
 *
 * <p>Its own class, against its own empty database, because the state cannot be reached
 * any other way: {@code CommunityPagesIT} shares one database across its tests, so by the
 * time any single test there runs, some other test has already posted a question and the
 * board is never empty. Creating an account does not put anything on the board, so the
 * only thing seeded here is a way to sign in.</p>
 *
 * <p>What it is for: the sprint 3 brief requires that "the community is empty" and "your
 * filter matched nothing" not be the same page. {@code CommunityPagesIT} covers the second;
 * this covers the first, and asserts that a fresh board does not claim a filter failed.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-community-empty-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommunityEmptyBoardIT {

    // Synthetic test-only credential, never an application default.
    private static final String PASSWORD = "TestPassword9";

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;

    @BeforeAll
    void accounts() {
        createAccount("newcomer");
    }

    @Test
    void anEmptyBoardInvitesTheFirstQuestionRatherThanBlamingAFilter() throws Exception {
        mvc.perform(get("/community").session(login("newcomer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No questions yet")))
                .andExpect(content().string(containsString(
                        "Questions asked by anyone on campus will appear here.")))
                // The filter-matched-nothing wording must not appear: nothing was filtered.
                .andExpect(content().string(not(containsString("No questions match this page or filter."))))
                // With no rows there is no count to print, and no page to turn.
                .andExpect(content().string(not(containsString(" shown"))))
                .andExpect(content().string(containsString("href=\"/community/questions/new\"")));
    }

    @Test
    void anEmptyBoardWithNoSearchTermIsStillSaidToBeEmptyEvenOnTheOtherTabs() throws Exception {
        // The "no questions yet" wording is for the unfiltered Latest tab only. Asking for
        // solved questions on a board with nothing on it has matched nothing, and says so -
        // which is the distinction the two empty states exist to make.
        mvc.perform(get("/community").param("filter", "SOLVED").session(login("newcomer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No questions match this page or filter.")))
                .andExpect(content().string(not(containsString("No questions yet"))));
    }

    @Test
    void anEmptyMyQuestionsPageIsNotTheEmptyBoard() throws Exception {
        mvc.perform(get("/community/mine").session(login("newcomer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have not asked anything yet")))
                .andExpect(content().string(not(containsString("No questions yet"))));
    }

    private void createAccount(String username) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username + " (test)");
        command.setPassword(PASSWORD);
        command.setRole(Role.REQUESTER);
        users.createUser(command, null);
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
