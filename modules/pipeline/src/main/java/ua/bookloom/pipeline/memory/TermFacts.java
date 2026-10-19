package ua.bookloom.pipeline.memory;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.checks.CharacterNames;

/**
 * The facts a prompt line states about a glossary term, worded once so the glossary lines and the rolling summary tell
 * a model the same thing: the type, and for a character only its gender, called {@code likely} when nobody confirmed it
 * (15i.T1). A place, a term or a title has no gender line.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TermFacts {

    /**
     * The facts of one term.
     *
     * @param type the term's type; never null
     * @param gender the term's gender; never null
     * @param origin who decided the gender; null when it is unknown
     * @return {@code character, likely female} or {@code place}; never null
     */
    public static String of(final TermType type, final Gender gender, @Nullable final GenderOrigin origin) {
        final String name = type.name().toLowerCase(Locale.ROOT);
        if (type != TermType.CHARACTER) {
            return name;
        }
        final String likely = origin == GenderOrigin.SUGGESTED ? CharacterNames.LIKELY + " " : "";
        return name + ", " + likely + gender.name().toLowerCase(Locale.ROOT);
    }
}
