package dev.ordonselli.raidboard.event;

import java.util.List;

import dev.ordonselli.raidboard.signup.SignupResponse;

/** A slot with its active sign-ups, each list in FIFO order: a waitlisted user's place is their index + 1. */
public record SlotResponse(
        Long id,
        String roleName,
        int capacity,
        List<SignupResponse> confirmed,
        List<SignupResponse> waitlisted,
        List<SignupResponse> pending) {
}
