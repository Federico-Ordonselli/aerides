package dev.ordonselli.aerides.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void codesHaveTheExpectedLengthAndOnlyUnambiguousCharacters() {
        String code = generator.generate();

        assertThat(code).hasSize(InviteCodeGenerator.LENGTH);
        assertThat(code.chars()).allMatch(c -> InviteCodeGenerator.ALPHABET.indexOf(c) >= 0);
        assertThat(code).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    void codesAreDifferentEachTime() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(1_000);
    }
}
