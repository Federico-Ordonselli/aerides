package dev.ordonselli.aerides.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import dev.ordonselli.aerides.common.validation.MaxUtf8Bytes;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt rejects passwords over 72 bytes: @Size counts characters, @MaxUtf8Bytes counts bytes
        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(min = 2, max = 50) String displayName,
        @Pattern(regexp = "it|en", message = "must be 'it' or 'en'") String locale) {
}
