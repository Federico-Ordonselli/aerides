package dev.ordonselli.raidboard.game;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.ordonselli.raidboard.common.error.ExternalServiceUnavailableException;
import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

class GameIT extends AbstractIntegrationTest {

    private static final RawgGame WITCHER = new RawgGame(3328, "The Witcher 3: Wild Hunt", "the-witcher-3-wild-hunt",
            "https://media.rawg.io/witcher3.jpg", "2015-05-18");
    private static final RawgGame BALDURS_GATE = new RawgGame(58617, "Baldur's Gate 3", "baldurs-gate-3", null,
            "2023-08-03");

    @Test
    void searchReturnsTheCatalogResults() throws Exception {
        TestUser user = newUser("Searcher");
        when(rawgClient.search("witcher")).thenReturn(List.of(WITCHER));

        mockMvc.perform(get("/api/games/search").param("q", "witcher").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rawgId").value(3328))
                .andExpect(jsonPath("$[0].coverUrl").value("https://media.rawg.io/witcher3.jpg"));
    }

    @Test
    void searchQueryMustBeAtLeastTwoCharacters() throws Exception {
        TestUser user = newUser("Searcher");

        mockMvc.perform(get("/api/games/search").param("q", "w").with(as(user)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rawgClient);
    }

    @Test
    void searchAnswers503WhenTheCatalogIsDown() throws Exception {
        TestUser user = newUser("Searcher");
        when(rawgClient.search(anyString()))
                .thenThrow(new ExternalServiceUnavailableException("The game catalog is unavailable, try again later"));

        mockMvc.perform(get("/api/games/search").param("q", "witcher").with(as(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service unavailable"));
    }

    @Test
    void gamesAreAddedOnceAndListedByName() throws Exception {
        TestUser owner = newUser("Owner");
        TestGroup group = createGroup(owner, "Catalog");
        when(rawgClient.getGame(3328)).thenReturn(WITCHER);
        when(rawgClient.getGame(58617)).thenReturn(BALDURS_GATE);

        addGame(owner, group, 3328).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("The Witcher 3: Wild Hunt"));
        addGame(owner, group, 58617).andExpect(status().isCreated());
        addGame(owner, group, 3328).andExpect(status().isConflict());

        mockMvc.perform(get("/api/groups/{id}/games", group.id()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Baldur's Gate 3"))
                .andExpect(jsonPath("$[1].name").value("The Witcher 3: Wild Hunt"));
    }

    @Test
    void nothingIsSavedWhenTheCatalogIsDown() throws Exception {
        TestUser owner = newUser("Owner");
        TestGroup group = createGroup(owner, "Catalog down");
        when(rawgClient.getGame(anyLong()))
                .thenThrow(new ExternalServiceUnavailableException("The game catalog is unavailable, try again later"));

        addGame(owner, group, 3328).andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/groups/{id}/games", group.id()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private org.springframework.test.web.servlet.ResultActions addGame(TestUser user, TestGroup group, long rawgId)
            throws Exception {
        return mockMvc.perform(post("/api/groups/{id}/games", group.id()).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawgId\": %d}".formatted(rawgId)));
    }
}
