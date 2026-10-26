package dev.ordonselli.raidboard.game;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.raidboard.common.security.CurrentUserId;

@RestController
@Tag(name = "Games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/api/games/search")
    @Operation(summary = "Search the RAWG catalog (503 if RAWG is unavailable or not configured)")
    public List<GameSearchResult> search(@RequestParam("q") @NotBlank @Size(min = 2, max = 100) String query) {
        return gameService.search(query);
    }

    @GetMapping("/api/groups/{groupId}/games")
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "List the games of a group")
    public List<GameResponse> list(@PathVariable Long groupId) {
        return gameService.listForGroup(groupId);
    }

    @PostMapping("/api/groups/{groupId}/games")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Add a RAWG game to the group catalog (OFFICER or OWNER)")
    public GameResponse add(@PathVariable Long groupId, @CurrentUserId Long userId,
            @Valid @RequestBody AddGameRequest request) {
        return gameService.addToGroup(groupId, userId, request.rawgId());
    }

    @DeleteMapping("/api/groups/{groupId}/games/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Remove a game from the group catalog (OFFICER or OWNER)")
    public void remove(@PathVariable Long groupId, @PathVariable Long gameId, @CurrentUserId Long userId) {
        gameService.removeFromGroup(groupId, userId, gameId);
    }
}
