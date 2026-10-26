package dev.ordonselli.raidboard.signup;

import jakarta.validation.constraints.NotNull;

public record SignupRequest(@NotNull Long slotId) {
}
