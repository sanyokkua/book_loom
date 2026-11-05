package ua.bookloom.pipeline.memory;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;

/**
 * The facts a prompt line states about a glossary term, worded once so every glossary line tells a model the same
 * thing: the type, and for a character its gender when the person set it, the book proved it or a
 * rule gave it. A gender the model only guessed is not stated, so a wrong guess is not spread over the book (design
 * D5; translation-pipeline "Write each injected map item as one line of its target, type and settled gender"). A
 * place, a term or a title has no gender.
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
     * @return {@code character, female}, {@code character} for a likely gender, or {@code place}; never null
     */
    public static String of(final TermType type, final Gender gender, @Nullable final Origin origin) {
        final String name = type.name().toLowerCase(Locale.ROOT);
        // The one place a glossary line decides whether a likely gender reaches a prompt: not by default; arm 4b
        // (task 7.8) decides whether it ever may.
        if (type != TermType.CHARACTER || origin == Origin.LIKELY) {
            return name;
        }
        return name + ", " + gender.name().toLowerCase(Locale.ROOT);
    }
}
