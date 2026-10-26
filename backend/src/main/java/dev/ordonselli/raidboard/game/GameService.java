package dev.ordonselli.raidboard.game;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntityType;
import dev.ordonselli.raidboard.audit.AuditService;
import dev.ordonselli.raidboard.common.error.ConflictException;
import dev.ordonselli.raidboard.common.error.NotFoundException;
import dev.ordonselli.raidboard.group.GroupRepository;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GroupRepository groupRepository;
    private final RawgClient rawgClient;
    private final GameMapper gameMapper;
    private final AuditService auditService;

    public GameService(GameRepository gameRepository, GroupRepository groupRepository, RawgClient rawgClient,
            GameMapper gameMapper, AuditService auditService) {
        this.gameRepository = gameRepository;
        this.groupRepository = groupRepository;
        this.rawgClient = rawgClient;
        this.gameMapper = gameMapper;
        this.auditService = auditService;
    }

    public List<GameSearchResult> search(String query) {
        return rawgClient.search(query).stream().map(GameSearchResult::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GameResponse> listForGroup(Long groupId) {
        return gameRepository.findAllByGroupIdOrderByNameAsc(groupId).stream().map(gameMapper::toResponse).toList();
    }

    @Transactional
    public GameResponse addToGroup(Long groupId, Long actorId, Long rawgId) {
        if (gameRepository.existsByGroupIdAndRawgId(groupId, rawgId)) {
            throw new ConflictException("This game is already in the group's catalog");
        }
        // Copy the data now: the catalog keeps working even when RAWG is down
        RawgGame rawgGame = rawgClient.getGame(rawgId);
        Game game = gameRepository.save(new Game(groupRepository.getReferenceById(groupId), rawgGame.id(),
                rawgGame.name(), rawgGame.slug(), rawgGame.backgroundImage()));
        auditService.record(groupId, actorId, AuditAction.GAME_ADDED, AuditEntityType.GAME, game.getId(),
                Map.of("name", game.getName(), "rawgId", rawgId));
        return gameMapper.toResponse(game);
    }

    @Transactional
    public void removeFromGroup(Long groupId, Long actorId, Long gameId) {
        Game game = gameRepository.findByIdAndGroupId(gameId, groupId)
                .orElseThrow(() -> new NotFoundException("Game not found"));
        auditService.record(groupId, actorId, AuditAction.GAME_REMOVED, AuditEntityType.GAME, gameId,
                Map.of("name", game.getName()));
        gameRepository.delete(game);
    }
}
