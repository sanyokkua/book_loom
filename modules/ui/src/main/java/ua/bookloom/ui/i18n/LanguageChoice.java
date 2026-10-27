package ua.bookloom.ui.i18n;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** What a person may pick as the language of the app: follow the computer, or one of the two catalogues. */
public enum LanguageChoice {

    /** No language is stored; the operating system decides at each launch. */
    SYSTEM(null),

    /** English, stored as {@code en}. */
    ENGLISH("en"),

    /** Ukrainian, stored as {@code uk}. */
    UKRAINIAN("uk");

    private final @Nullable String tag;

    LanguageChoice(final @Nullable String tag) {
        this.tag = tag;
    }

    /**
     * The choice a stored value stands for.
     *
     * @param stored the stored language tag, or empty when nothing is stored
     * @return the matching choice; {@link #SYSTEM} for nothing stored or a tag no catalogue exists for
     */
    public static LanguageChoice of(final Optional<String> stored) {
        Objects.requireNonNull(stored, "stored");
        for (final LanguageChoice choice : values()) {
            if (choice.tag != null && stored.filter(choice.tag::equals).isPresent()) {
                return choice;
            }
        }
        return SYSTEM;
    }

    /**
     * The tag that is stored for this choice.
     *
     * @return {@code en} or {@code uk}; empty for {@link #SYSTEM}, which stores nothing
     */
    public Optional<String> tag() {
        return Optional.ofNullable(tag);
    }

    /**
     * The language this choice displays on a given computer.
     *
     * @param os the operating system's locale; never null
     * @return the chosen language, or the one the operating system chooses for {@link #SYSTEM}
     */
    public Locale resolve(final Locale os) {
        Objects.requireNonNull(os, "os");
        return tag == null ? OsLocaleProvider.forOs(os) : Locale.forLanguageTag(tag);
    }
}
