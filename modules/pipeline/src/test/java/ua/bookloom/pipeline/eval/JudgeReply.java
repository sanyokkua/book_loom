package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What the code read from one judge reply. Only {@link Scored} and {@link Preferred} carry a judgement; anything the
 * parser could not read whole is {@link Unparsed}, so a malformed reply never becomes a score.
 */
sealed interface JudgeReply {

    /** One line naming what was read, for the log and the tests. */
    String describe();

    /** How much an error costs: each severity carries its word in the reply and its MQM penalty points. */
    enum Severity {
        /** Noticed, but the meaning and the reading are intact. */
        MINOR("minor", 1),
        /** The meaning distorted or the sentence plainly wrong. */
        MAJOR("major", 5),
        /** The opposite or unrelated meaning, a missing sentence, or not the target language. */
        CRITICAL("critical", 25);

        private final String wire;
        private final int weight;

        Severity(final String wire, final int weight) {
            this.wire = wire;
            this.weight = weight;
        }

        int weight() {
            return weight;
        }

        /** The severity named by {@code word} in any letter case, or empty when it names none. */
        static Optional<Severity> of(final String word) {
            return Stream.of(values())
                    .filter(s -> s.wire.equalsIgnoreCase(word.strip()))
                    .findFirst();
        }
    }

    /** The judge's overall verdict on one translation, with its word in the reply. */
    enum Verdict {
        /** Publishable as it stands. */
        GOOD("good"),
        /** Small fixes only. */
        MINOR_ISSUES("minor-issues"),
        /** A sentence must be rewritten. */
        MAJOR_ISSUES("major-issues"),
        /** Translate again. */
        UNUSABLE("unusable");

        private final String wire;

        Verdict(final String wire) {
            this.wire = wire;
        }

        String wire() {
            return wire;
        }

        /** The verdict named by {@code word} in any letter case, or empty when it names none. */
        static Optional<Verdict> of(final String word) {
            return Stream.of(values())
                    .filter(v -> v.wire.equalsIgnoreCase(word.strip()))
                    .findFirst();
        }
    }

    /** The better of two translations as the reply labelled them, with its word in the reply. */
    enum Winner {
        /** The translation shown as A. */
        A("A"),
        /** The translation shown as B. */
        B("B"),
        /** Neither is better. */
        TIE("tie");

        private final String wire;

        Winner(final String wire) {
            this.wire = wire;
        }

        /** The word a reply and a report use, {@code A}, {@code B} or {@code tie}. */
        String wire() {
            return wire;
        }

        /** The winner named by {@code word} in any letter case, or empty when it names none. */
        static Optional<Winner> of(final String word) {
            return Stream.of(values())
                    .filter(w -> w.wire.equalsIgnoreCase(word.strip()))
                    .findFirst();
        }
    }

    /**
     * One error the judge found, whose span is an exact substring of the judged translation.
     *
     * @param span the translation's words that show the error
     * @param severity how much it costs
     * @param errorClass the judge's class of the error, such as {@code grammar}
     */
    record JudgeError(String span, Severity severity, String errorClass) {

        /** Rejects a missing part. */
        public JudgeError {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(errorClass, "errorClass");
        }
    }

    /**
     * The four scores, each a whole number from 0 to 5.
     *
     * @param meaning the source's statements kept
     * @param grammar correct target-language grammar
     * @param terminology the glossary and names kept
     * @param fluency reads as written in the target language
     */
    record Scores(int meaning, int grammar, int terminology, int fluency) {}

    /**
     * The defects the judge says the translation shows; a flag the reply left out is false.
     *
     * @param meaningError a statement says something else
     * @param omission something of the source is missing
     * @param addition something the source does not say
     * @param sourceLanguageResidue untranslated or source-language words remain
     * @param formattingBroken a placeholder, dialogue punctuation or heading number is broken
     */
    record Flags(
            boolean meaningError,
            boolean omission,
            boolean addition,
            boolean sourceLanguageResidue,
            boolean formattingBroken) {}

    /**
     * A single translation's judgement, read whole.
     *
     * @param analysis the judge's reasoning, as written
     * @param errors the errors whose span the translation holds
     * @param scores the four scores
     * @param flags the defect flags
     * @param verdict the overall verdict
     */
    record Scored(String analysis, List<JudgeError> errors, Scores scores, Flags flags, Verdict verdict)
            implements JudgeReply {

        /** Copies the errors. */
        public Scored {
            Objects.requireNonNull(analysis, "analysis");
            errors = List.copyOf(errors);
            Objects.requireNonNull(scores, "scores");
            Objects.requireNonNull(flags, "flags");
            Objects.requireNonNull(verdict, "verdict");
        }

        /** The MQM penalty computed by the code from the kept errors: minor 1, major 5, critical 25. */
        int mqmPenalty() {
            return errors.stream().mapToInt(e -> e.severity().weight()).sum();
        }

        @Override
        public String describe() {
            return String.format(
                    Locale.ROOT,
                    "scored meaning=%d grammar=%d terminology=%d fluency=%d errors=%d mqm=%d verdict=%s",
                    scores.meaning(),
                    scores.grammar(),
                    scores.terminology(),
                    scores.fluency(),
                    errors.size(),
                    mqmPenalty(),
                    verdict.wire());
        }
    }

    /**
     * A comparison's answer, read whole.
     *
     * @param analysis the judge's reasoning, as written
     * @param winner the label the judge preferred
     */
    record Preferred(String analysis, Winner winner) implements JudgeReply {

        /** Rejects a missing part. */
        public Preferred {
            Objects.requireNonNull(analysis, "analysis");
            Objects.requireNonNull(winner, "winner");
        }

        @Override
        public String describe() {
            return "winner=" + winner.wire;
        }
    }

    /**
     * A reply the code could not read whole; it counts as no judgement at all.
     *
     * @param reason why, for the log
     */
    record Unparsed(String reason) implements JudgeReply {

        /** Rejects a missing reason. */
        public Unparsed {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String describe() {
            return "unparsed";
        }
    }
}
