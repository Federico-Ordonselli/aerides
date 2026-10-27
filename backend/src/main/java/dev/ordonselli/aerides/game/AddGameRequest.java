package dev.ordonselli.aerides.game;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddGameRequest(@NotNull @Positive Long rawgId) {
}
