package dev.ordonselli.aerides.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A game as returned by the RAWG API. Only the fields we use are mapped. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawgGame(
        long id,
        String name,
        String slug,
        @JsonProperty("background_image") String backgroundImage,
        String released) {
}
