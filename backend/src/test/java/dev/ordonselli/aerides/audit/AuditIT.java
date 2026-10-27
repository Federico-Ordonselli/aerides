package dev.ordonselli.aerides.audit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.ordonselli.aerides.support.AbstractIntegrationTest;
import dev.ordonselli.aerides.support.TestGroup;
import dev.ordonselli.aerides.support.TestUser;

/** The audit page endpoint. Who may call it is covered by the access matrix in GroupAccessIT. */
class AuditIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    private TestUser owner;
    private TestUser member;
    private TestGroup group;

    /** Log of the group, oldest first: GROUP_CREATED (owner), MEMBER_JOINED (member), MEMBER_ROLE_CHANGED (owner). */
    @BeforeEach
    void createGroupHistory() throws Exception {
        owner = newUser("Owner");
        member = newUser("Member");
        group = createGroup(owner, "Audit test");
        joinGroup(member, group);
        changeRole(owner, group, member, "OFFICER");
        // Another group's history must never show up here
        createGroup(newUser("Stranger"), "Other group");
    }

    @Test
    void theLogIsReturnedNewestFirstWithTheActorsName() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/audit", group.id()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].action")
                        .value(contains("MEMBER_ROLE_CHANGED", "MEMBER_JOINED", "GROUP_CREATED")))
                .andExpect(jsonPath("$.content[*].actorName").value(contains("Owner", "Member", "Owner")))
                .andExpect(jsonPath("$.content[0].entityType").value("MEMBERSHIP"))
                .andExpect(jsonPath("$.content[0].details.from").value("MEMBER"))
                .andExpect(jsonPath("$.content[0].details.to").value("OFFICER"));
    }

    @Test
    void theLogCanBeFilteredByActionAndPaginated() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/audit", group.id()).with(as(owner)).param("action", "MEMBER_JOINED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action").value(contains("MEMBER_JOINED")));

        mockMvc.perform(get("/api/groups/{id}/audit", group.id()).with(as(owner))
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].action").value(contains("GROUP_CREATED")));
    }

    @Test
    void theClientCannotChangeTheOrder() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/audit", group.id()).with(as(owner)).param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("MEMBER_ROLE_CHANGED"));
    }

    @Test
    void anUnknownActionIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/audit", group.id()).with(as(owner)).param("action", "HACKED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entriesOfADeletedUserStayWithoutAName() throws Exception {
        jdbcClient.sql("DELETE FROM users WHERE id = :id").param("id", member.id()).update();

        mockMvc.perform(get("/api/groups/{id}/audit", group.id()).with(as(owner)).param("action", "MEMBER_JOINED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").value(member.id()))
                .andExpect(jsonPath("$.content[0].actorName").value(nullValue()));
    }
}
