package ua.bookloom.util.lang;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Read-only access to the {@link Language} catalogue for the Book Brief's searchable list and every caller that
 * needs to look a normalized tag up.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Languages {

    /**
     * The whole language catalogue.
     *
     * @return all 34 catalogued languages, in {@link Language} declaration order
     */
    public static List<Language> all() {
        return List.of(Language.values());
    }

    /**
     * Looks a language up by its exact normalized tag — the caller is expected to have already called
     * {@link LanguageTags#normalize(String)}; this method performs no normalization of its own.
     *
     * @param tag the tag to look up, compared exactly (e.g. {@code "zh-Hans"}, not {@code "zh-CN"})
     * @return the catalogued language whose tag equals {@code tag}, or empty if none does
     */
    public static Optional<Language> byTag(String tag) {
        Objects.requireNonNull(tag, "tag");
        return all().stream().filter(language -> language.tag().equals(tag)).findFirst();
    }

    /**
     * Normalizes a raw tag and looks its script up in one step — the one place every caller that only needs a
     * language's script (the token estimator, the length band, the quality checks) goes, instead of each keeping
     * its own normalize-then-lookup copy.
     *
     * @param tag the raw tag, possibly {@code null}, blank, or naming no catalogued language
     * @return the tag's {@link Script}, or empty when {@code tag} normalizes to no catalogued {@link Language}
     */
    public static Optional<Script> scriptOf(@Nullable final String tag) {
        return LanguageTags.normalize(tag).flatMap(Languages::byTag).map(Language::script);
    }
}
