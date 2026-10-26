package dev.ordonselli.raidboard.group;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

/** Turns a group name into a URL-friendly slug: "Gilda dei Draghi!" becomes "gilda-dei-draghi". */
@Component
public class SlugGenerator {

    private static final int MAX_LENGTH = 60;
    private static final String FALLBACK = "group";

    public String slugify(String name) {
        // Split accented letters into letter + accent ("à" -> "a" + "`"), then drop the accents
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }
}
