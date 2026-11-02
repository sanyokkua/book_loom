package ua.bookloom.pipeline.prompt;

import static ua.bookloom.pipeline.prompt.SlotNames.AUTHOR;
import static ua.bookloom.pipeline.prompt.SlotNames.CHARACTERS;
import static ua.bookloom.pipeline.prompt.SlotNames.EXAMPLES;
import static ua.bookloom.pipeline.prompt.SlotNames.FOREIGN_PASSAGE_RULE;
import static ua.bookloom.pipeline.prompt.SlotNames.LANGUAGE_RULES;
import static ua.bookloom.pipeline.prompt.SlotNames.SOURCE;
import static ua.bookloom.pipeline.prompt.SlotNames.SOURCE_LANGUAGE;
import static ua.bookloom.pipeline.prompt.SlotNames.STYLE_SHEET;
import static ua.bookloom.pipeline.prompt.SlotNames.SUMMARY_SLOT;
import static ua.bookloom.pipeline.prompt.SlotNames.TARGET_LANGUAGE;
import static ua.bookloom.pipeline.prompt.SlotNames.TERMS;
import static ua.bookloom.pipeline.prompt.SlotNames.TITLE;
import static ua.bookloom.pipeline.prompt.SlotNames.TOKENS;

import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.glossary.LexiconReadingSchema;
import ua.bookloom.pipeline.glossary.NameKnowledgeSchema;
import ua.bookloom.pipeline.glossary.TermChoiceSchema;
import ua.bookloom.pipeline.glossary.TermReadingSchema;
import ua.bookloom.pipeline.setup.BookKnowledgeSchema;
import ua.bookloom.pipeline.setup.BriefSuggestionSchema;
import ua.bookloom.pipeline.setup.BriefSynthesisSchema;

/**
 * One owner per model call's template files, call kind, response-format name and sampling temperature, so no call
 * goes out at a temperature the design never chose.
 */
// Slots is a record of defensively copied immutable sets; Error Prone cannot see that without @Immutable.
@SuppressWarnings("ImmutableEnumChecker")
public enum PromptName {

    /** The first translation attempt; a review retry may ask for the lower temperature. */
    DRAFT(
            "draft",
            CallKind.DRAFT,
            "draft",
            DraftSchema.SCHEMA,
            new Temperatures(0.2, 0.1),
            new Slots(
                    Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE),
                    Set.of(EXAMPLES, LANGUAGE_RULES, "tokenRules", "noTokenRules")),
            new Slots(
                    Set.of(SOURCE, "target", "text"),
                    Set.of(
                            TOKENS,
                            SUMMARY_SLOT,
                            "glossaryTerms",
                            "lockedNames",
                            "suggestedTerms",
                            "memoryHint",
                            "lexiconTerms",
                            CHARACTERS,
                            "precedingTargets",
                            "followingTarget",
                            "extraInstruction"))),

    /**
     * A batch of consecutive segments drafted in one call, answered as one JSON object with an entry per id; the flat
     * schema carries no length limits, because a bounded schema once stalled a structured call on one provider.
     */
    DRAFT_BATCH_JSON("draft-batch-json", "draft-batch-json", BatchSchema.SCHEMA, BatchSlots.USER),

    /** The correction call after a reply is not the required JSON object. */
    STRUCTURAL_REPAIR(
            "structural-repair",
            CallKind.STRUCTURAL_REPAIR,
            "structural-repair",
            DraftSchema.SCHEMA,
            new Temperatures(0.2, null),
            null,
            new Slots(Set.of("rejectedReply", "diagnostic"), Set.of())),

    /** The correction call after a target fails the placeholder-multiset gate. */
    PLACEHOLDER_REPAIR(
            "placeholder-repair",
            CallKind.PLACEHOLDER_REPAIR,
            "placeholder-repair",
            DraftSchema.SCHEMA,
            new Temperatures(0.2, null),
            null,
            new Slots(Set.of("rejectedTarget", TOKENS), Set.of("gateNote"))),

    /**
     * Reviews a chunk's drafted pairs once per chunk and answers per pair {@code ok}, find-and-replace edits or a
     * rewrite; its temperature is zero, because the answer must repeat, and its output limit grows with the pairs.
     */
    REVIEWER(
            "reviewer",
            CallKind.REVIEW,
            "reviewer",
            ReviewerSchema.SCHEMA,
            new Temperatures(0.0, null),
            new Slots(
                    Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE),
                    Set.of(LANGUAGE_RULES)),
            new Slots(
                    Set.of("pairs"),
                    Set.of("glossaryTerms", "suggestedTerms", "usualRenderings", CHARACTERS, "passFocus"))),

    /** The self-heal call that rewrites one rejected target to fix its concrete, named findings. */
    DIRECTED_FIX(
            "directed-fix",
            CallKind.DIRECTED_FIX,
            "directed-fix",
            DraftSchema.SCHEMA,
            new Temperatures(0.2, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE), Set.of(EXAMPLES)),
            new Slots(Set.of(SOURCE, "text", "findings"), Set.of("expectedTokens"))),

    /**
     * Backward revision's re-render of one decided segment whose character's gender became known after it was
     * drafted; the facts block names each such character and gender, since the style sheet cannot.
     */
    REVISION(
            "revision",
            CallKind.REVISION,
            "revision",
            DraftSchema.SCHEMA,
            new Temperatures(0.2, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE), Set.of(EXAMPLES)),
            new Slots(Set.of(SOURCE, "text"), Set.of("resolvedFacts", TOKENS))),

    /**
     * The consistency pass's check of one decided paragraph against the translated paragraph before it and after it, the
     * names and terms the book holds and the source: it fixes what disagrees and returns the rest unchanged.
     */
    CONSISTENCY(
            "consistency",
            CallKind.REVISION,
            "consistency",
            ConsistencySchema.SCHEMA,
            new Temperatures(0.1, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE), Set.of(EXAMPLES)),
            new Slots(
                    Set.of(SOURCE, "text"),
                    Set.of("resolvedFacts", "previous", "next", "lexiconTerms", CHARACTERS, SUMMARY_SLOT, TOKENS))),

    /** The Fast dial's choice among recurring words (15i.N7): keep or drop, a batch of candidates a call. */
    TERM_CHOICE(
            "term-choice",
            CallKind.TERM_CHOICE,
            "term-choice",
            TermChoiceSchema.SCHEMA,
            new Temperatures(0.1, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE), Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of(TERMS), Set.of())),

    /**
     * The most probable gender and kind of bare names, with no book text (design D5): a batch of names a call, at
     * temperature zero because the same name must get the same answer.
     */
    NAME_KNOWLEDGE(
            "name-knowledge",
            CallKind.NAME_KNOWLEDGE,
            "name-knowledge",
            NameKnowledgeSchema.SCHEMA,
            new Temperatures(0.0, null),
            new Slots(Set.of(), Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of("names"), Set.of())),

    /** The proposal of the translated book's file name, asked for on the Export screen; one small call. */
    FILE_NAME(
            "file-name",
            CallKind.FILE_NAME,
            "file-name",
            DraftSchema.SCHEMA,
            new Temperatures(0.2, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE), Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of("fileName"), Set.of(TITLE, AUTHOR, "titleTarget", "authorTarget", "correction"))),

    /** The proposal of the Book Brief's tone and style from one passage of the book; one small call per passage. */
    BRIEF_SUGGESTION(
            "brief-suggestion",
            CallKind.BRIEF_SUGGESTION,
            "brief-suggestion",
            BriefSuggestionSchema.SCHEMA,
            new Temperatures(0.1, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE), Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of("sample", "dialogueShare"), Set.of(TITLE, AUTHOR, "askLanguage"))),

    /** The question what the model knows of the book by its title and author; one small call, checked against the metadata. */
    BOOK_KNOWLEDGE(
            "book-knowledge",
            CallKind.BOOK_KNOWLEDGE,
            "book-knowledge",
            BookKnowledgeSchema.SCHEMA,
            new Temperatures(0.0, null),
            new Slots(Set.of(), Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of(TITLE), Set.of(AUTHOR))),

    /**
     * The one call that decides the brief's genre, audience and voice from the book's facts, the accepted knowledge of
     * it and the passages' verified readings, each value with the basis the code then checks (15i.B4).
     */
    BRIEF_SYNTHESIS(
            "brief-synthesis",
            CallKind.BRIEF_SYNTHESIS,
            "brief-synthesis",
            BriefSynthesisSchema.SCHEMA,
            new Temperatures(0.0, null),
            new Slots(Set.of(), Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of("reports"), Set.of(TITLE, AUTHOR, "year", "publisher", "description", "knowledge"))),

    /** The reading of one glossary term in numbered windows from across the book (15i.N4); one term per call. */
    TERM_READING(PreparationSlots.READING, "term-reading", TermReadingSchema.SCHEMA, "windows"),

    /** The reading of one recurring term in numbered windows from across the book (15i.N7); one term per call. */
    LEXICON_READING(PreparationSlots.READING, "lexicon-reading", LexiconReadingSchema.SCHEMA, "windows"),

    /** At Max, one term's disagreeing reading calls summed up in one call (15i.N5). */
    TERM_SUMMARY(PreparationSlots.SUMMARY, "term-summary", TermReadingSchema.SUMMARY, "calls"),

    /** The name and term proposal the person asks for; a batch of candidates per call, never run by itself. */
    PRESCAN(
            "prescan",
            CallKind.PRESCAN,
            "prescan",
            PrescanSchema.SCHEMA,
            new Temperatures(0.2, null),
            PreparationSlots.PRESCAN,
            new Slots(Set.of("candidates"), Set.of("existingTerms"))),

    /**
     * The glossary review the person asks for: a batch of held terms, each with how often the book uses it and where,
     * judged a name, a term or not a name, with a type and a gender guess.
     */
    REVIEW_TERMS(
            "review-terms",
            CallKind.REVIEW_TERMS,
            "review-terms",
            ReviewTermsSchema.SCHEMA,
            new Temperatures(0.1, null),
            PreparationSlots.READING,
            new Slots(Set.of(TERMS), Set.of())),

    /**
     * The glossary's target suggestions, run after the review's or the name scan's verdicts: a batch of held names and
     * terms, each with one sentence from the book, given a rendering by the Book Brief's name policy and a gender.
     */
    SUGGEST_TARGETS(
            "suggest-targets",
            CallKind.SUGGEST_TARGETS,
            "suggest-targets",
            SuggestTargetsSchema.SCHEMA,
            new Temperatures(0.1, null),
            new Slots(
                    Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE, "nameRule"),
                    Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE, EXAMPLES, LANGUAGE_RULES)),
            new Slots(Set.of(TERMS), Set.of())),

    /** The optional garbled-word check: finished target texts answered with the words that are not real words. */
    SUSPICIOUS_WORDS(
            "suspicious-words",
            CallKind.REVIEW,
            "suspicious-words",
            SuspiciousWordsSchema.SCHEMA,
            new Temperatures(0.0, null),
            new Slots(Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE), Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE)),
            new Slots(Set.of("items"), Set.of())),

    /** The chapter-end summary the Max dial asks the model for; the reply's target text is what later prompts carry. */
    SUMMARY(
            "summary",
            CallKind.SUMMARY,
            "summary",
            SummarySchema.SCHEMA,
            new Temperatures(0.2, null),
            new Slots(
                    Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE),
                    Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE, LANGUAGE_RULES)),
            new Slots(Set.of("chapterSource", "chapterTarget"), Set.of("previousSummary")));

    /**
     * The temperatures of a call.
     *
     * @param base the temperature the call goes out at
     * @param lower the lower temperature a review retry may ask for; {@code null} when there is none
     */
    private record Temperatures(double base, @Nullable Double lower) {}

    private final String resourceBaseName;
    private final CallKind callKind;
    private final String responseFormatName;
    private final String responseSchema;
    private final double temperature;
    private final @Nullable Double lowerTemperature;
    private final @Nullable Slots systemSlots;
    private final Slots userSlots;

    // A reading of one glossary term (15i.N4, 15i.N5): a term reading at temperature 0, the term and one text slot.
    PromptName(final Slots system, final String name, final String responseSchema, final String textSlot) {
        this(
                name,
                CallKind.TERM_READING,
                name,
                responseSchema,
                new Temperatures(0.0, null),
                system,
                new Slots(Set.of("term", textSlot), Set.of()));
    }

    // A batch draft (15i.X1 has three formats): the draft call kind and temperatures, the batch system slots.
    PromptName(final String name, final String format, final String schema, final Slots user) {
        this(name, CallKind.DRAFT, format, schema, new Temperatures(0.2, 0.1), BatchSlots.SYSTEM, user);
    }

    PromptName(
            final String resourceBaseName,
            final CallKind callKind,
            final String responseFormatName,
            final String responseSchema,
            final Temperatures temperatures,
            @Nullable final Slots systemSlots,
            final Slots userSlots) {
        this.resourceBaseName = resourceBaseName;
        this.callKind = callKind;
        this.responseFormatName = responseFormatName;
        this.responseSchema = responseSchema;
        this.temperature = temperatures.base();
        this.lowerTemperature = temperatures.lower();
        this.systemSlots = systemSlots;
        this.userSlots = userSlots;
    }

    /** The template files' name without the {@code .system.prompt}/{@code .user.prompt} suffix. */
    public String resourceBaseName() {
        return resourceBaseName;
    }

    /** The kind of call this template serves. */
    public CallKind callKind() {
        return callKind;
    }

    /** The response-format name the pseudo model switches on. */
    public String responseFormatName() {
        return responseFormatName;
    }

    /** The JSON schema this call's structured response must match. */
    public String responseSchema() {
        return responseSchema;
    }

    /**
     * Returns this call's sampling temperature.
     *
     * @param lower {@code true} to ask for the lower temperature a retry uses
     * @return the temperature to send
     * @throws IllegalArgumentException if {@code lower} is asked for and this call has no lower temperature
     */
    public double temperature(final boolean lower) {
        if (!lower) {
            return temperature;
        }
        if (lowerTemperature == null) {
            throw new IllegalArgumentException(name() + " has no lower temperature");
        }
        return lowerTemperature;
    }

    /** Whether the {@code examples} slot holds name-suggestion examples rather than translation examples. */
    boolean showsNameExamples() {
        return this == SUGGEST_TARGETS;
    }

    /** Whether the call reviews a translation, so its language rules include the reviewer checks. */
    boolean reviewsTranslation() {
        return this == REVIEWER;
    }

    /** The system template's slots, or empty when the call reuses another call's system message. */
    public Optional<Slots> systemSlots() {
        return Optional.ofNullable(systemSlots);
    }

    /** The user template's slots. */
    public Slots userSlots() {
        return userSlots;
    }
}
