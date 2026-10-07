package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;

/**
 * When a detected first-person narration deserves a question: the one rule the Book Brief's notice and the Start
 * translation question share, so they never disagree about it.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NarratorHints {

    /**
     * Whether the source was found to be told in the first person while the brief neither says the narrator's gender
     * nor says the narrator is third person.
     *
     * @param book the open book, or null when none is open
     * @param brief its brief, or null when none is open
     * @return {@code true} when the narrator's gender is worth asking about, {@code false} otherwise
     */
    public static boolean needsGender(final @Nullable OpenedBook book, final @Nullable BookBrief brief) {
        final NarratorHint hint = book == null ? null : book.narratorHint();
        return hint != null
                && hint.suggestsFirstPerson()
                && brief != null
                && brief.narrator().person() != NarratorPerson.THIRD
                && brief.narrator().gender() == Gender.UNKNOWN;
    }

    /**
     * Whether a narrator the person has not touched should start as "first person" because the source says "I".
     *
     * @param book the open book, or null when none is open
     * @param brief its brief, or null when none is open
     * @return {@code true} when the brief's narrator is unspecified and the hint says first person or mixed
     */
    public static boolean preselectsFirstPerson(final @Nullable OpenedBook book, final @Nullable BookBrief brief) {
        final NarratorHint hint = book == null ? null : book.narratorHint();
        return hint != null
                && hint.suggestsFirstPerson()
                && brief != null
                && brief.narrator().person() == NarratorPerson.UNSPECIFIED;
    }
}
