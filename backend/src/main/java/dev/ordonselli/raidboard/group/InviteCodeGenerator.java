package dev.ordonselli.raidboard.group;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Random invite codes such as "K7WQ3MZP2X".
 * <p>
 * The alphabet leaves out characters that are easy to confuse when copied by hand (0/O, 1/I/L).
 * 31 symbols and 10 characters give about 8 * 10^14 combinations: guessing a valid code is not feasible.
 * {@link SecureRandom} is used because the codes must not be predictable.
 */
@Component
public class InviteCodeGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
