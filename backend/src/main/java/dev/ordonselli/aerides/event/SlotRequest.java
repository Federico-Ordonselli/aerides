package dev.ordonselli.aerides.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A slot in a create/update request. {@code id} is null for a new slot, and ignored on create. */
public record SlotRequest(
        Long id,
        @NotBlank @Size(max = 40) String roleName,
        @NotNull @Min(1) @Max(100) Integer capacity) {
}
