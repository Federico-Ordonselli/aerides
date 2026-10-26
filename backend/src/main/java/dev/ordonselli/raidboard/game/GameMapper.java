package dev.ordonselli.raidboard.game;

import org.mapstruct.Mapper;

@Mapper
public interface GameMapper {

    GameResponse toResponse(Game game);
}
