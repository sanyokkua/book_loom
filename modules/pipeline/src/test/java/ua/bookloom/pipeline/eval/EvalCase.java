package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.QaFinding;

/** One prompt-eval case: a draft, a directed fix or a judge pair, each sent through its production prompt builder. */
sealed interface EvalCase {

    /** The case's short name, the first column of the report. */
    String name();

    /**
     * What a translated reply must show beyond parsing and keeping its tokens.
     *
     * @param copy {@code true} when the text has no words to translate and must come back unchanged
     * @param marker a regular expression the reply must match — proof it translated the text, used a glossary
     *     rendering, kept a drop cap on one letter or did not obey an instruction inside the book text; empty for none
     * @param injection {@code true} when the text is an instruction aimed at the model, which must be translated
     */
    record Expect(boolean copy, String marker, boolean injection) {

        /** Validates the marker. */
        public Expect {
            Objects.requireNonNull(marker, "marker");
        }

        static Expect translate() {
            return new Expect(false, "", false);
        }

        static Expect copied() {
            return new Expect(true, "", false);
        }

        static Expect containing(final String marker) {
            return new Expect(false, marker, false);
        }

        static Expect injection(final String marker) {
            return new Expect(false, marker, true);
        }
    }

    /**
     * A draft of one masked segment.
     *
     * @param name the case name
     * @param masked the masked source shown to the model
     * @param glossary the glossary lines injected into the prompt
     * @param expect what the reply must show
     */
    record Draft(String name, String masked, List<String> glossary, Expect expect) implements EvalCase {

        /** Copies the glossary. */
        public Draft {
            glossary = List.copyOf(glossary);
        }
    }

    /**
     * A directed fix of a rejected target.
     *
     * @param name the case name
     * @param masked the masked source
     * @param rejected the rejected target to fix
     * @param findings the findings the fix must address
     * @param expect what the reply must show
     */
    record Fix(String name, String masked, String rejected, List<QaFinding> findings, Expect expect)
            implements EvalCase {

        /** Copies the findings. */
        public Fix {
            findings = List.copyOf(findings);
        }
    }

    /**
     * A judge case: the same source judged once with a good and once with a bad candidate, in separate calls.
     *
     * @param name the case name
     * @param masked the masked source
     * @param good a faithful, fluent candidate
     * @param bad a candidate with a meaning error, an omission or text left untranslated
     */
    record Judge(String name, String masked, String good, String bad) implements EvalCase {}
}
