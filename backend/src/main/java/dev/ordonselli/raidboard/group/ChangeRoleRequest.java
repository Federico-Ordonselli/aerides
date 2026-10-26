package dev.ordonselli.raidboard.group;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull MembershipRole role) {
}
