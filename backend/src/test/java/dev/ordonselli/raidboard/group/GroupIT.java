package dev.ordonselli.raidboard.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntry;
import dev.ordonselli.raidboard.audit.AuditEntryRepository;
import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

class GroupIT extends AbstractIntegrationTest {

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Test
    void groupLifecycleIsAuditedAndTheLogSurvivesDeletion() throws Exception {
        TestUser owner = newUser("Aragorn");
        TestUser member = newUser("Boromir");

        TestGroup group = createGroup(owner, "Fellowship");
        joinGroup(member, group);

        mockMvc.perform(get("/api/groups").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Fellowship"))
                .andExpect(jsonPath("$[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$[0].memberCount").value(2));

        mockMvc.perform(get("/api/groups/{id}/members", group.id()).with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Aragorn"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].displayName").value("Boromir"))
                // Members see display names only, never each other's email
                .andExpect(jsonPath("$[0].email").doesNotExist());

        mockMvc.perform(patch("/api/groups/{id}", group.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Fellowship of the Ring\", \"description\": \"One raid to rule them all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fellowship of the Ring"))
                // The slug stays the same, so existing links keep working
                .andExpect(jsonPath("$.slug").value("fellowship"));

        mockMvc.perform(delete("/api/groups/{id}", group.id()).with(as(owner))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/groups/{id}", group.id()).with(as(owner))).andExpect(status().isNotFound());

        List<AuditEntry> log = auditEntryRepository.findAllByGroupIdOrderByIdAsc(group.id());
        assertThat(log).extracting(AuditEntry::getAction).containsExactly(
                AuditAction.GROUP_CREATED, AuditAction.MEMBER_JOINED, AuditAction.GROUP_UPDATED,
                AuditAction.GROUP_DELETED);
        assertThat(log.get(2).getDetails()).containsKey("name").containsKey("description");
        assertThat(log.get(3).getActorId()).isEqualTo(owner.id());
    }

    @Test
    void joinRules() throws Exception {
        TestUser owner = newUser("Owner");
        TestUser user = newUser("Joiner");
        TestGroup group = createGroup(owner, "Join rules");

        // Codes are case-insensitive
        mockMvc.perform(post("/api/groups/join").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"%s\"}".formatted(group.inviteCode().toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.inviteCode").doesNotExist());

        mockMvc.perform(post("/api/groups/join").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"%s\"}".formatted(group.inviteCode())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/groups/join").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"WRONGCODE2\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void regeneratingTheInviteCodeInvalidatesTheOldOne() throws Exception {
        TestUser owner = newUser("Owner");
        TestUser late = newUser("Latecomer");
        TestGroup group = createGroup(owner, "Rotating code");

        String body = mockMvc.perform(post("/api/groups/{id}/invite-code", group.id()).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newCode = JsonPath.read(body, "$.inviteCode");
        assertThat(newCode).isNotEqualTo(group.inviteCode());

        mockMvc.perform(post("/api/groups/join").with(as(late)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"%s\"}".formatted(group.inviteCode())))
                .andExpect(status().isNotFound());
        joinGroup(late, new TestGroup(group.id(), newCode));
    }

    @Test
    void discordWebhookIsValidatedAndOnlyTheOwnerCanReadIt() throws Exception {
        TestUser owner = newUser("Owner");
        TestUser officer = newUser("Officer");
        TestGroup group = createGroup(owner, "Webhook");
        joinGroup(officer, group);
        changeRole(owner, group, officer, "OFFICER");

        // Not a Discord URL: rejected, so the server can never be pointed at internal addresses
        mockMvc.perform(put("/api/groups/{id}/discord-webhook", group.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"http://169.254.169.254/latest/meta-data\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());

        String webhook = "https://discord.com/api/webhooks/123456/secret-token";
        mockMvc.perform(put("/api/groups/{id}/discord-webhook", group.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"%s\"}".formatted(webhook)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/{id}", group.id()).with(as(owner)))
                .andExpect(jsonPath("$.discordWebhookUrl").value(webhook));
        mockMvc.perform(get("/api/groups/{id}", group.id()).with(as(officer)))
                .andExpect(jsonPath("$.hasDiscordWebhook").value(true))
                .andExpect(jsonPath("$.discordWebhookUrl").doesNotExist())
                .andExpect(jsonPath("$.inviteCode").value(group.inviteCode()));
    }

    @Test
    void ownerRulesAreEnforced() throws Exception {
        TestUser owner = newUser("Owner");
        TestUser member = newUser("Member");
        TestGroup group = createGroup(owner, "Owner rules");
        joinGroup(member, group);

        mockMvc.perform(patch("/api/groups/{id}/members/{userId}", group.id(), member.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(delete("/api/groups/{id}/members/{userId}", group.id(), owner.id()).with(as(owner)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(delete("/api/groups/{id}/members/me", group.id()).with(as(owner)))
                .andExpect(status().isUnprocessableContent());
    }
}
