package ua.bookloom.pipeline.reviewer;

import java.util.Objects;

/** What {@link EditVerifier} concluded about one edit. */
public sealed interface Verification {

    /**
     * The edit holds: its quote is the one place it names, it keeps every token, and the checks do not get worse.
     *
     * @param edited the whole candidate with the edit applied
     */
    record Verified(String edited) implements Verification {

        /** Rejects a missing text. */
        public Verified {
            Objects.requireNonNull(edited, "edited");
        }
    }

    /**
     * The edit points at nothing the candidate holds, or changes nothing: a hallucinated quote, ignored.
     *
     * @param why a short reason for the log
     */
    record Ignored(String why) implements Verification {

        /** Rejects a missing reason. */
        public Ignored {
            Objects.requireNonNull(why, "why");
        }
    }

    /**
     * The edit's quote is in the candidate but applying it was refused, so the reviewer's finding stands as evidence
     * and a directed fix may repair it.
     *
     * @param reason what failed
     */
    record Failed(FailureReason reason) implements Verification {

        /** Rejects a missing reason. */
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Why an edit whose quote was found was not applied. */
    enum FailureReason {

        /** The quote occurs more than once, so the app cannot tell which place was meant. */
        AMBIGUOUS_QUOTE("the quote occurs more than once"),

        /** The replacement drops, adds, repeats or reorders a placeholder token. */
        TOKENS_CHANGED("the replacement changes a placeholder token"),

        /** A hard gate refused the edited text. */
        CHECKS_REFUSED("the edited text fails a hard gate"),

        /** The edited text has a blocking check the candidate did not have. */
        BLOCKERS_GREW("the edit introduces a blocking defect"),

        /** The edited text writes a word twice in a row that the candidate did not. */
        DOUBLED_WORD("the edit writes a word twice in a row"),

        /** The edit lowercases a sentence start or capitalises a word inside a sentence. */
        CASE_CHANGED("the edit changes the case of a sentence start or a word"),

        /** The edit brings in a letter of another script than the candidate's own. */
        FOREIGN_SCRIPT("the edit brings in a letter of another script"),

        /** The edit changes the number of quote marks, dialogue dashes, sentences or words. */
        COUNTS_CHANGED("the edit changes the quote marks, dashes, sentences or words"),

        /** The edit re-inflects or replaces a glossary rendering. */
        GLOSSARY_RENDERING("the edit changes a glossary rendering"),

        /** A meaning edit replaces a word with one that shares no stem and is not a glossary rendering. */
        MEANING_SWAP("the edit swaps a word for a different one");

        private final String label;

        FailureReason(final String label) {
            this.label = label;
        }

        /**
         * A short phrase for a finding's note.
         *
         * @return what failed, in plain words
         */
        public String label() {
            return label;
        }
    }
}
