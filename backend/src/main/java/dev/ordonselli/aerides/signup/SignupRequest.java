package dev.ordonselli.aerides.signup;

import jakarta.validation.constraints.NotNull;

public record SignupRequest(@NotNull Long slotId) {
}
