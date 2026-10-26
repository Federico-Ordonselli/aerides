package dev.ordonselli.raidboard.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinGroupRequest(@NotBlank @Size(max = 16) String inviteCode) {
}
