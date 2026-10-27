package dev.ordonselli.aerides.group;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull MembershipRole role) {
}
