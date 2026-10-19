package ua.bookloom.pipeline.reviewer;

import java.util.List;
import java.util.Objects;

/**
 * What the book offers a reviewer call besides its pairs, cut to the pairs of that call.
 */
@FunctionalInterface
public interface ReviewerSheets {

    /**
     * The terms and the character sheet one reviewer call is shown.
     *
     * @param terms the glossary, suggested and usual renderings the pairs name
     * @param characters the {@code name — gender} lines of the characters the pairs name
     */
    record Sheet(ReviewerCall.Terms terms, List<String> characters) {

        /** Copies the list. */
        public Sheet {
            Objects.requireNonNull(terms, "terms");
            characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
        }
    }

    /**
     * Cuts the book's terms and characters to some pairs.
     *
     * @param pairs the pairs of one call; never empty
     * @return what that call is shown; never null
     */
    Sheet forPairs(List<ReviewedPair> pairs);

    /**
     * A sheet that is the same for every set of pairs, for a caller that holds ready lines.
     *
     * @param terms the terms every call is shown
     * @param characters the character lines every call is shown
     * @return sheets that ignore the pairs
     */
    static ReviewerSheets fixed(final ReviewerCall.Terms terms, final List<String> characters) {
        final Sheet sheet = new Sheet(terms, characters);
        return _ -> sheet;
    }
}
