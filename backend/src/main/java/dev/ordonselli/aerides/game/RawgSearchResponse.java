package dev.ordonselli.aerides.game;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawgSearchResponse(List<RawgGame> results) {
}
