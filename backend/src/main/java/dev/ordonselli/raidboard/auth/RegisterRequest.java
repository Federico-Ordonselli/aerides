package dev.ordonselli.raidboard.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 50) String displayName,
        @Pattern(regexp = "it|en", message = "must be 'it' or 'en'") String locale) {
}
