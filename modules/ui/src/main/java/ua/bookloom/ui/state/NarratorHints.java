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
     * Whether the source was found to be told in the first person (the pipeline's rule for a mixed book: at
     * least a quarter of the judged chapters) while the brief neither says the narrator's gender
     * nor says the narrator is third person.
     *
     * @param hint what the open book's text says, or null when none is open or it was not judged
     * @param brief its brief, or null when none is open
     * @return {@code true} when the narrator's gender is worth asking about, {@code false} otherwise
     */
    public static boolean needsGender(final @Nullable NarratorHint hint, final @Nullable BookBrief brief) {
        return hint != null
                && hint.hasFirstPersonShare()
                && brief != null
                && brief.narrator().person() != NarratorPerson.THIRD
                && brief.narrator().gender() == Gender.UNKNOWN;
    }

    /**
     * Whether a narrator the person has not touched should start as "first person" because the source says "I".
     *
     * @param hint what the open book's text says, or null when none is open or it was not judged
     * @param brief its brief, or null when none is open
     * @return {@code true} when the brief's narrator is unspecified and the hint says first person, or mixed with at least a quarter
     *     of the judged chapters in the first person
     */
    public static boolean preselectsFirstPerson(final @Nullable NarratorHint hint, final @Nullable BookBrief brief) {
        return hint != null
                && hint.hasFirstPersonShare()
                && brief != null
                && brief.narrator().person() == NarratorPerson.UNSPECIFIED;
    }
}
