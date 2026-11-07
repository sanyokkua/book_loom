package ua.bookloom.pipeline.revision;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.NameForms;

/**
 * Which written form of a pinned name's new target replaces each form of its previous one: the form that shares the
 * case ending, so {@code до Хейла} becomes {@code до Гейла} and not {@code до Гейл}. A new form is taken only when the
 * target language's table also generates it for the new target; a form with no such partner is left as written.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PinnedForms {

    /**
     * Pairs each form of the previous target with the same case of the new one.
     *
     * @param previous the non-blank previous target
     * @param target the non-blank new target
     * @param gender the entry's gender, which chooses the declension; unknown allows both
     * @param language the target language tag, or null when it is not known (then only the targets pair)
     * @return the previous form to the new form, the previous target first; never empty
     */
    static Map<String, String> renamings(
            final String previous, final String target, final Gender gender, @Nullable final String language) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(gender, "gender");
        final Set<String> newForms = NameForms.forms(target, gender, language).all();
        final Map<String, String> renamings = new LinkedHashMap<>();
        renamings.put(previous, target);
        for (final String form : NameForms.forms(previous, gender, language).all()) {
            final String renamed = sameCase(previous, form, target);
            if (renamed != null && newForms.contains(renamed)) {
                renamings.putIfAbsent(form, renamed);
            }
        }
        log.debug("Pinned forms language={} gender={} pairs={}", language, gender, renamings.size());
        log.trace("Pinned forms of {} → {}: {}", previous, target, renamings);
        return Map.copyOf(renamings);
    }

    // The ending a form adds after the part it shares with the previous target, put on the new target in place of the
    // previous target's own ending; null when the new target does not end in that ending.
    private static @Nullable String sameCase(final String previous, final String form, final String target) {
        int shared = 0;
        while (shared < previous.length() && shared < form.length() && previous.charAt(shared) == form.charAt(shared)) {
            shared++;
        }
        final String previousEnding = previous.substring(shared);
        if (!target.endsWith(previousEnding)) {
            return null;
        }
        return target.substring(0, target.length() - previousEnding.length()) + form.substring(shared);
    }
}
