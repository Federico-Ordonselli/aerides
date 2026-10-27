package dev.ordonselli.aerides.game;

/** A RAWG search hit, not yet part of any group. */
public record GameSearchResult(Long rawgId, String name, String slug, String coverUrl, String released) {

    static GameSearchResult from(RawgGame game) {
        return new GameSearchResult(game.id(), game.name(), game.slug(), game.backgroundImage(), game.released());
    }
}
