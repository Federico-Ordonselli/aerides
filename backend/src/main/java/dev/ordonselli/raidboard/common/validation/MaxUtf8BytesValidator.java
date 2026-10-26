package dev.ordonselli.raidboard.common.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int maxBytes;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
        this.maxBytes = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null is valid by convention: @NotBlank/@NotNull decide whether a value is required
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
