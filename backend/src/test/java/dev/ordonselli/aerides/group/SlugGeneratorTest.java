package dev.ordonselli.aerides.group;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugGeneratorTest {

    private final SlugGenerator slugGenerator = new SlugGenerator();

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Gilda dei Draghi!       | gilda-dei-draghi",
            "  La Città   Perduta    | la-citta-perduta",
            "D&D — Venerdì sera      | d-d-venerdi-sera",
            "!!!                     | group",
            "Raid 2026: Phase #2     | raid-2026-phase-2"
    })
    void slugify(String name, String expected) {
        assertThat(slugGenerator.slugify(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource("aaaaaaaaaa bbbbbbbbbb cccccccccc dddddddddd eeeeeeeeee ffffffffff gggggggggg")
    void longNamesAreCutWithoutATrailingDash(String name) {
        String slug = slugGenerator.slugify(name);
        assertThat(slug).hasSizeLessThanOrEqualTo(60).doesNotEndWith("-");
    }
}
