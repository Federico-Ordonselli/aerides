package dev.ordonselli.aerides.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The string must take at most {@code value} bytes in UTF-8.
 * <p>
 * {@code @Size} counts characters, but some limits are in bytes: BCrypt rejects passwords over 72 bytes,
 * and "é" is one character but two bytes.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "must be at most {value} bytes long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
