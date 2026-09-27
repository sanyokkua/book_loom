package ua.bookloom.util.lang;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
}
