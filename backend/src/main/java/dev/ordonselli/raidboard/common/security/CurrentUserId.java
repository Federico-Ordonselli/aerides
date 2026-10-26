package dev.ordonselli.raidboard.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injects the id of the authenticated user into a controller method parameter.
 * <p>
 * The principal is the decoded JWT, whose "sub" claim holds the user id.
 * Usage: {@code public UserResponse me(@CurrentUserId Long userId)}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface CurrentUserId {
}
