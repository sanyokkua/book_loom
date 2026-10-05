package ua.bookloom.pipeline.qa;

/**
 * The quality-gate checks — five soft checks blended into confidence and four hard gates that never enter the
 * blend — each carrying the wire vocabulary a {@link ua.bookloom.api.project.QaFinding} names its cause by
 * ({@code raisedBy}), the {@link ua.bookloom.api.project.QaFinding#kind()} it raises, and its confidence weight.
 */
public enum CheckName {

    /** Whether the target's letters are written in the target language's script. */
    SCRIPT(0.20, "language", "script", false),

    /** Whether the target merely copies its source back untranslated. */
    ECHO(0.15, "language", "echo", false),

    /** Whether the target is caught in a decode-loop repetition. */
    REPETITION(0.10, "fluency", "repetition", false),

    /** Whether the target's length sits inside the language pair's expected band. */
    LENGTH(0.25, "omission", "length", false),

    /** Whether every locked glossary term present in the segment is rendered as entered. */
    GLOSSARY(0.30, "glossary", "glossary", false),

    /** Hard gate: the target is empty or opens with a refusal phrase. */
    REFUSAL(0.0, "meaning", "refusal", true),

    /** Hard gate: placeholder integrity — multiset, pair order and nesting. */
    PLACEHOLDER(0.0, "markup", "placeholder", true),

    /** Hard gate: a locked term's protected span did not come back exactly once. */
    LOCKED_TERM(0.0, "glossary", "locked-term", true),

    /** Hard gate: a kept-foreign-run protected span did not come back exactly once. */
    KEPT_RUN(0.0, "markup", "kept-run", true),

    /** Text check, blocking: a word mixing the target script with a letter of another script or a digit. */
    SCRIPT_PURITY(0.0, "language", "script-purity", true),

    /** Text check, blocking: a quote pair the source balanced that the target leaves open, stray or crossed. */
    QUOTE_BALANCE(0.0, "fluency", "quote-balance", true),

    /** Text check, blocking: a paragraph of at least 12 words still in the source language. */
    LANGUAGE_IDENTITY(0.0, "language", "language-identity", true),

    /** Text check, soft: the same word twice in a row. */
    DUPLICATE_WORD(0.0, "fluency", "duplicate-word", false),

    /** Text check, soft: a doubled space, or a space before a full stop or comma or inside a bracket. */
    SPACING(0.0, "fluency", "spacing", false),

    /** Text check, soft: a past-tense word after the narrator's «я» whose gender is not the narrator's. */
    GENDER(0.0, "gender", "gender", false),

    /** Text check, soft: a word the word validator doubts is a real word of the target language. */
    UNKNOWN_WORD(0.0, "fluency", "unknown-word", false),

    /** Note only: the typography pass changed the target's apostrophes, ellipses, quote marks or spacing. */
    TYPOGRAPHY(0.0, "fluency", "normalised", false);

    private final double weight;
    private final String findingKind;
    private final String raisedBy;
    private final boolean hardGate;

    CheckName(final double weight, final String findingKind, final String raisedBy, final boolean hardGate) {
        this.weight = weight;
        this.findingKind = findingKind;
        this.raisedBy = raisedBy;
        this.hardGate = hardGate;
    }

    /**
     * The confidence weight.
     *
     * @return this check's weight in {@code qa.Confidence.blend}; {@code 0.0} for a hard gate, which never enters
     *     the blend
     */
    public double weight() {
        return weight;
    }

    /**
     * The {@link ua.bookloom.api.project.QaFinding#kind()} this check raises on failure.
     *
     * @return the finding kind, e.g. {@code "language"} or {@code "markup"}
     */
    public String findingKind() {
        return findingKind;
    }

    /**
     * The {@link ua.bookloom.api.project.QaFinding#raisedBy()} wire string for a finding this check raises.
     *
     * @return this check's wire-vocabulary name
     */
    public String raisedBy() {
        return raisedBy;
    }

    /**
     * Whether this check is a hard gate.
     *
     * @return {@code true} for {@link #REFUSAL}, {@link #PLACEHOLDER}, {@link #LOCKED_TERM} or {@link #KEPT_RUN};
     *     {@code false} for a soft check that carries a confidence weight
     */
    public boolean isHardGate() {
        return hardGate;
    }
}
