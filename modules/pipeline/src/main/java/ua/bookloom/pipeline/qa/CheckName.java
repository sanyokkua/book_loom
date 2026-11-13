package ua.bookloom.pipeline.qa;

/**
 * The quality-gate checks — five soft checks blended into confidence, the hard gates and the deterministic text checks
 * — each carrying the wire vocabulary a {@link ua.bookloom.api.project.QaFinding} names its cause by
 * ({@code raisedBy}), the {@link ua.bookloom.api.project.QaFinding#kind()} it raises, its confidence weight and its
 * {@link CheckLevel}. This is the one level table (design D12): nothing else says what blocks.
 */
public enum CheckName {

    /** Whether the target's letters are written in the target language's script. */
    SCRIPT(0.20, FindingKinds.LANGUAGE, "script", CheckLevel.BLOCK),

    /** Whether the target merely copies its source back untranslated. */
    ECHO(0.15, FindingKinds.LANGUAGE, "echo", CheckLevel.BLOCK),

    /** Whether the target is caught in a decode-loop repetition. */
    REPETITION(0.10, FindingKinds.FLUENCY, "repetition", CheckLevel.BLOCK),

    /** Whether the target's length sits inside the language pair's expected band. */
    LENGTH(0.25, FindingKinds.OMISSION, "length", CheckLevel.BLOCK),

    /** Whether every locked glossary term present in the segment is rendered as entered. */
    GLOSSARY(0.30, FindingKinds.GLOSSARY, "glossary", CheckLevel.BLOCK),

    /** Hard gate: the target is empty or opens with a refusal phrase. */
    REFUSAL(0.0, FindingKinds.MEANING, "refusal", CheckLevel.BLOCK),

    /** Hard gate: placeholder integrity — multiset, pair order and nesting. */
    PLACEHOLDER(0.0, FindingKinds.MARKUP, "placeholder", CheckLevel.BLOCK),

    /** Hard gate: a locked term's protected span did not come back exactly once. */
    LOCKED_TERM(0.0, FindingKinds.GLOSSARY, "locked-term", CheckLevel.BLOCK),

    /** Hard gate: a kept-foreign-run protected span did not come back exactly once. */
    KEPT_RUN(0.0, FindingKinds.MARKUP, "kept-run", CheckLevel.BLOCK),

    /** Text check, blocking: a word mixing the target script with a letter of another script or a digit. */
    SCRIPT_PURITY(0.0, FindingKinds.LANGUAGE, "script-purity", CheckLevel.BLOCK),

    /** Text check, blocking: a quote pair the source balanced that the target leaves open, stray or crossed. */
    QUOTE_BALANCE(0.0, FindingKinds.FLUENCY, "quote-balance", CheckLevel.BLOCK),

    /** Text check, blocking: a paragraph of at least 12 words still in the source language. */
    LANGUAGE_IDENTITY(0.0, FindingKinds.LANGUAGE, "language-identity", CheckLevel.BLOCK),

    /** Text check, blocking: a significant sentence of the source with no counterpart in the target. */
    SENTENCE_COUNT(0.0, FindingKinds.OMISSION, "sentence-count", CheckLevel.BLOCK),

    /** Text check, blocking: a glossary name the source calls out that the target has lost. */
    VOCATIVE(0.0, FindingKinds.OMISSION, "vocative", CheckLevel.BLOCK),

    /** Text check, blocking: reply structure (braces, brackets, a wrapper label) left inside the text; a format defect, not a meaning one. */
    PROTOCOL_LEAK(0.0, FindingKinds.MARKUP, "protocol-leak", CheckLevel.BLOCK),

    /** Text check, blocking: a glossary name of the source replaced by another glossary name in the target. */
    NAME_SWAP(0.0, FindingKinds.GLOSSARY, "name-swap", CheckLevel.BLOCK),

    /** Text check, note: the same word twice in a row. */
    DUPLICATE_WORD(0.0, FindingKinds.FLUENCY, "duplicate-word", CheckLevel.NOTE),

    /** Text check, note: a doubled space, or a space before a full stop or comma or inside a bracket. */
    SPACING(0.0, FindingKinds.FLUENCY, "spacing", CheckLevel.NOTE),

    /** Text check, repair: a past-tense word after the narrator's «я» whose gender is not the narrator's. */
    GENDER(0.0, FindingKinds.GENDER, "gender", CheckLevel.REPAIR),

    /** Text check, repair: a woman's name followed by a masculine verb or declined like a man's name. */
    NAME_GENDER(0.0, FindingKinds.GENDER, "name-gender", CheckLevel.REPAIR),

    /** Text check, blocking: a UUID, hexadecimal string, ISBN, web address or e-mail address the target changed or lost. */
    IDENTIFIER(0.0, FindingKinds.MEANING, "identifier", CheckLevel.BLOCK),

    /**
     * Text check: a number the source writes in digits that the target changed or lost. Its level is BLOCK (design D12:
     * a replaced leading number blocks); a lost number of two digits is raised at medium severity (repair), an extra
     * one at low severity (note).
     */
    NUMBER(0.0, FindingKinds.MEANING, "number", CheckLevel.BLOCK),

    /** Text check, blocking: a run of Latin words of the source left as it is in a target of another script. */
    LATIN_RUN(0.0, FindingKinds.LANGUAGE, "latin-run", CheckLevel.BLOCK),

    /** Text check, repair: a glossary name of the source that the target has lost. */
    NAME_MISSING(0.0, FindingKinds.GLOSSARY, "name-missing", CheckLevel.REPAIR),

    /**
     * Text check: a glossary name the target spells another way, with the exact replacement. A pinned name blocks
     * (high severity); a suggested name is only a note (low severity).
     */
    NAME_SPELLING(0.0, FindingKinds.GLOSSARY, "name-spelling", CheckLevel.BLOCK),

    /** Text check, repair: function words only the source language has, left in the target (что, это in Ukrainian). */
    FUNCTION_WORD_RESIDUE(0.0, FindingKinds.LANGUAGE, "function-word-residue", CheckLevel.REPAIR),

    /** Text check, repair: one quoted speech that mixes ти-forms and capitalised Ви-forms for one listener. */
    ADDRESS_MIX(0.0, FindingKinds.LANGUAGE, "address-mix", CheckLevel.REPAIR),

    /** Text check, note: a word the word validator doubts is a real word of the target language. */
    UNKNOWN_WORD(0.0, FindingKinds.FLUENCY, "unknown-word", CheckLevel.NOTE),

    /** Text check, blocking: a word with a letter outside the target language's alphabet (ы ъ э ё in Ukrainian). */
    ALPHABET(0.0, FindingKinds.LANGUAGE, "alphabet", CheckLevel.BLOCK),

    /** Note only: the typography pass changed the target's apostrophes, ellipses, quote marks or spacing. */
    TYPOGRAPHY(0.0, FindingKinds.FLUENCY, "normalised", CheckLevel.NOTE),

    /** Text check, repair: a paragraph whose last mark is another class than its source's. */
    TERMINAL_MARK(0.0, FindingKinds.OMISSION, "terminal-mark", CheckLevel.REPAIR),

    /** Text check, repair: a speech whose form of address the target changed between informal and formal. */
    ADDRESS_PARITY(0.0, FindingKinds.LANGUAGE, "address-parity", CheckLevel.REPAIR);

    private final double weight;
    private final String findingKind;
    private final String raisedBy;
    private final CheckLevel level;

    CheckName(final double weight, final String findingKind, final String raisedBy, final CheckLevel level) {
        this.weight = weight;
        this.findingKind = findingKind;
        this.raisedBy = raisedBy;
        this.level = level;
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
     * The level of this check's findings at high severity; a check whose findings differ in level (numbers, names,
     * gender) raises the lower ones at medium (repair) or low (note) severity, read back by {@link CheckLevels#of}.
     *
     * @return the level of a high-severity finding of this check
     */
    public CheckLevel level() {
        return level;
    }
}
