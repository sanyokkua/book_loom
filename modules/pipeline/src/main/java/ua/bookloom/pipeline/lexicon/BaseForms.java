package ua.bookloom.pipeline.lexicon;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Picks the dictionary-like form among the surface forms one stem was met in. Without a dictionary the nearest guess is
 * the form the others extend by one letter (господар for господаря, господарю); a form that only longer forms extend by
 * two letters or more is the cut-off of a longer word (облич of обличчя) and is no rendering. Among forms nothing
 * extends, the shortest of the common ones is the nearest to the dictionary form (консоль, not консолей), then the
 * most used.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BaseForms {

    /** A form is common when it is used in at least a quarter of the most used form's uses. */
    private static final int COMMON_FORM_SHARE = 4;

    /**
     * The base form of a stem's forms.
     *
     * @param forms the lower-cased surface forms with their uses
     * @param obliqueEndings endings of the target language's oblique case forms, which lose to a form with none; empty
     *     for no preference
     * @return the base form, or empty when every form is a cut-off
     */
    static Optional<String> of(final Map<String, Integer> forms, final List<String> obliqueEndings) {
        final int most =
                forms.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return forms.keySet().stream()
                .filter(form -> !isCutOff(form, forms))
                .filter(form -> forms.getOrDefault(form, 0) * COMMON_FORM_SHARE >= most)
                .max(Comparator.comparingInt((String form) -> isOblique(form, obliqueEndings) ? 0 : 1)
                        .thenComparingInt(form -> extendedByOne(form, forms))
                        .thenComparing(Comparator.comparingInt(String::length).reversed())
                        .thenComparingInt(form -> forms.getOrDefault(form, 0))
                        .thenComparing(Comparator.<String>naturalOrder().reversed()));
    }

    private static boolean isOblique(final String form, final List<String> obliqueEndings) {
        return obliqueEndings.stream().anyMatch(form::endsWith);
    }

    private static int extendedByOne(final String form, final Map<String, Integer> forms) {
        return forms.entrySet().stream()
                .filter(other -> other.getKey().length() == form.length() + 1
                        && other.getKey().startsWith(form))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private static boolean isCutOff(final String form, final Map<String, Integer> forms) {
        final boolean longer = forms.keySet().stream()
                .anyMatch(other -> other.length() >= form.length() + 2 && other.startsWith(form));
        return longer && extendedByOne(form, forms) == 0;
    }
}
