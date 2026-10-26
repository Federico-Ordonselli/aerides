package dev.ordonselli.raidboard.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 1000) String description) {
}
