package dev.ordonselli.aerides.signup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.aerides.common.security.CurrentUserId;

@RestController
@RequestMapping("/api/groups/{groupId}/events/{eventId}/signups")
@Tag(name = "Sign-ups")
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "Sign up for a slot: confirmed, waitlisted if full, or pending if approval is required")
    public SignupResponse signUp(@PathVariable Long groupId, @PathVariable Long eventId,
            @CurrentUserId Long userId, @Valid @RequestBody SignupRequest request) {
        return signupService.signUp(groupId, eventId, userId, request.slotId());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "Withdraw; a freed seat goes to the first waitlisted user (422 after the deadline)")
    public void withdraw(@PathVariable Long groupId, @PathVariable Long eventId, @CurrentUserId Long userId) {
        signupService.withdraw(groupId, eventId, userId);
    }

    @PostMapping("/{signupId}/approve")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Approve a pending sign-up (OFFICER or OWNER)")
    public SignupResponse approve(@PathVariable Long groupId, @PathVariable Long eventId,
            @PathVariable Long signupId, @CurrentUserId Long userId) {
        return signupService.approve(groupId, eventId, signupId, userId);
    }

    @PostMapping("/{signupId}/reject")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Reject a pending sign-up (OFFICER or OWNER)")
    public SignupResponse reject(@PathVariable Long groupId, @PathVariable Long eventId,
            @PathVariable Long signupId, @CurrentUserId Long userId) {
        return signupService.reject(groupId, eventId, signupId, userId);
    }
}
