package ua.bookloom.ui.state;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The registry id a new provider gets: a readable slug of its name, never one already in use. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below
// (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProviderIds {

    private static final String FALLBACK = "provider";

    /**
     * Makes an id from a name. The slug is ASCII only, so a name in another script falls back to a plain word.
     *
     * @param name what the person typed; may be blank
     * @param taken the ids already in the registry
     * @return the slug, or the slug with {@code -2}, {@code -3}, … when it is taken; never blank
     */
    public static String newId(final String name, final Collection<String> taken) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(taken, "taken");
        final String slug =
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        final String base = slug.isEmpty() ? FALLBACK : slug;
        String candidate = base;
        int next = 2;
        while (taken.contains(candidate)) {
            candidate = base + "-" + next++;
        }
        return candidate;
    }
}
