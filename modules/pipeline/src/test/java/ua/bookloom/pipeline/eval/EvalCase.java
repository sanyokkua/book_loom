package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.TermType;

/**
 * One prompt-eval case: a draft, a directed fix, a review pair or a batch of glossary names, each sent through its
 * production prompt builder.
 */
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
     * A draft of one segment, built the way a run builds it: the segment's inline markup is already masked, its glossary
     * names are hidden or listed by the production context code, and the context around it comes from {@code context}.
     *
     * @param name the case name
     * @param masked the segment's text as the document model masks it: inline markup is {@code ⟦gN⟧}; a locked name is
     *     written out, since the run hides it behind its own token
     * @param glossary the glossary entries the run holds, of which the ones the text names are shown or hidden
     * @param expect what the reply must show
     * @param context what surrounds the segment: earlier pairs, summary, lexicon and narrator
     */
    record Draft(String name, String masked, List<EvalTerm> glossary, Expect expect, EvalContext context)
            implements EvalCase {

        /** Copies the glossary and fills an absent context. */
        public Draft {
            glossary = glossary == null ? List.of() : List.copyOf(glossary);
            context = context == null ? EvalContext.none() : context;
        }

        Draft(final String name, final String masked, final List<EvalTerm> glossary, final Expect expect) {
            this(name, masked, glossary, expect, EvalContext.none());
        }
    }

    /** Which repair call a {@link Repair} case sends. */
    enum RepairStep {
        /** The correction after a reply that was not the required JSON object. */
        STRUCTURAL,
        /** The correction after a target that fails the placeholder gate. */
        PLACEHOLDER
    }

    /**
     * A draft's repair call, built through the run's own request factory from a reply the run would refuse.
     *
     * @param name the case name
     * @param step which repair
     * @param masked the segment's text with its inline markup masked
     * @param rejected the refused reply: the raw reply for a structural repair, the target text for a placeholder one
     * @param expect what the repaired reply must show
     */
    record Repair(String name, RepairStep step, String masked, String rejected, Expect expect) implements EvalCase {}

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
     * A review case: the same source reviewed once with a good and once with a bad candidate, in separate calls.
     *
     * @param name the case name
     * @param masked the masked source
     * @param good a faithful, fluent candidate
     * @param bad a candidate with a meaning error, an omission or text left untranslated
     */
    record Review(String name, String masked, String good, String bad) implements EvalCase {}

    /**
     * A batch of glossary names given suggested targets under one name policy; each name becomes its own report row.
     *
     * @param name the case name
     * @param policy the Book Brief name policy the suggestions follow
     * @param sentences the book text the names occur in
     * @param names each name, its type and what its suggestion must match
     */
    record Suggest(String name, NamePolicy policy, List<String> sentences, List<SuggestedName> names)
            implements EvalCase {

        /** Copies the lists. */
        public Suggest {
            sentences = List.copyOf(sentences);
            names = List.copyOf(names);
        }
    }

    /**
     * One name of a {@link Suggest} case.
     *
     * @param term the name as the glossary holds it
     * @param type its glossary type
     * @param marker a regular expression the whole suggested target must match: the dictionary form in the target
     *     script, uninflected
     */
    record SuggestedName(String term, TermType type, String marker) {}
}
