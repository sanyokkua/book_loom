package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

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
            0.2,
            0.1,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"),
                    Set.of("examples", "languageRules")),
            new Slots(
                    Set.of("source", "target", "tokens", "text"),
                    Set.of(
                            "summary",
                            "glossaryTerms",
                            "lockedNames",
                            "suggestedTerms",
                            "memoryHint",
                            "lexiconTerms",
                            "characters",
                            "precedingTargets",
                            "extraInstruction"))),

    /**
     * A batch of consecutive segments drafted in one call, answered as one JSON object with an entry per id; the flat
     * schema carries no length limits, because a bounded schema once stalled a structured call on one provider.
     */
    DRAFT_BATCH_JSON(
            "draft-batch-json",
            CallKind.DRAFT,
            "draft-batch-json",
            BatchSchema.SCHEMA,
            0.2,
            0.1,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"),
                    Set.of("examples", "languageRules")),
            BatchSlots.USER),

    /** The correction call after a reply is not the required JSON object. */
    STRUCTURAL_REPAIR(
            "structural-repair",
            CallKind.STRUCTURAL_REPAIR,
            "structural-repair",
            DraftSchema.SCHEMA,
            0.2,
            null,
            null,
            new Slots(Set.of("rejectedReply", "diagnostic"), Set.of())),

    /** The correction call after a target fails the placeholder-multiset gate. */
    PLACEHOLDER_REPAIR(
            "placeholder-repair",
            CallKind.PLACEHOLDER_REPAIR,
            "placeholder-repair",
            DraftSchema.SCHEMA,
            0.2,
            null,
            null,
            new Slots(Set.of("rejectedTarget", "tokens"), Set.of("gateNote"))),

    /**
     * Reviews a chunk's drafted pairs once per chunk and answers per pair {@code ok}, find-and-replace edits or a
     * rewrite; its temperature is zero, because the answer must repeat, and its output limit grows with the pairs.
     */
    REVIEWER(
            "reviewer",
            CallKind.REVIEW,
            "reviewer",
            ReviewerSchema.SCHEMA,
            0.0,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"),
                    Set.of("languageRules")),
            new Slots(Set.of("pairs"), Set.of("glossaryTerms", "characters", "passFocus"))),

    /** The self-heal call that rewrites one rejected target to fix its concrete, named findings. */
    DIRECTED_FIX(
            "directed-fix",
            CallKind.DIRECTED_FIX,
            "directed-fix",
            DraftSchema.SCHEMA,
            0.2,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of("examples")),
            new Slots(Set.of("source", "text", "findings"), Set.of("expectedTokens"))),

    /**
     * Backward revision's re-render of one decided segment whose character's gender became known after it was
     * drafted; the facts block names each such character and gender, since the style sheet cannot.
     */
    REVISION(
            "revision",
            CallKind.REVISION,
            "revision",
            DraftSchema.SCHEMA,
            0.2,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of("examples")),
            new Slots(Set.of("source", "text"), Set.of("resolvedFacts", "tokens"))),

    /**
     * The consistency pass's check of one decided paragraph against the translated paragraph before it and after it, the
     * names and terms the book holds and the source: it fixes what disagrees and returns the rest unchanged.
     */
    CONSISTENCY(
            "consistency",
            CallKind.REVISION,
            "consistency",
            DraftSchema.SCHEMA,
            0.1,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of("examples")),
            new Slots(Set.of("source", "text"), Set.of("resolvedFacts", "previous", "next", "tokens"))),

    /** The name and term proposal the person asks for; a batch of candidates per call, never run by itself. */
    PRESCAN(
            "prescan",
            CallKind.PRESCAN,
            "prescan",
            PrescanSchema.SCHEMA,
            0.2,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage"),
                    Set.of("styleSheet", "foreignPassageRule", "languageRules")),
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
            0.1,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage"), Set.of("styleSheet", "foreignPassageRule")),
            new Slots(Set.of("terms"), Set.of())),

    /**
     * The glossary's target suggestions, run after the review's or the name scan's verdicts: a batch of held names and
     * terms, each with one sentence from the book, given a rendering by the Book Brief's name policy and a gender.
     */
    SUGGEST_TARGETS(
            "suggest-targets",
            CallKind.SUGGEST_TARGETS,
            "suggest-targets",
            SuggestTargetsSchema.SCHEMA,
            0.1,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage", "nameRule"),
                    Set.of("styleSheet", "foreignPassageRule", "examples", "languageRules")),
            new Slots(Set.of("terms"), Set.of())),

    /**
     * The optional garbled-word check: a batch of finished target texts, answered with the words that are
     * not real words, each with the phrase that holds it; sampled at zero because the answer must repeat.
     */
    SUSPICIOUS_WORDS(
            "suspicious-words",
            CallKind.REVIEW,
            "suspicious-words",
            SuspiciousWordsSchema.SCHEMA,
            0.0,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage"), Set.of("styleSheet", "foreignPassageRule")),
            new Slots(Set.of("items"), Set.of())),

    /** The chapter-end summary the Max dial asks the model for; the reply's target text is what later prompts carry. */
    SUMMARY(
            "summary",
            CallKind.SUMMARY,
            "summary",
            SummarySchema.SCHEMA,
            0.2,
            null,
            new Slots(
                    Set.of("sourceLanguage", "targetLanguage"),
                    Set.of("styleSheet", "foreignPassageRule", "languageRules")),
            new Slots(Set.of("chapterSource", "chapterTarget"), Set.of("previousSummary")));

    private final String resourceBaseName;
    private final CallKind callKind;
    private final String responseFormatName;
    private final String responseSchema;
    private final double temperature;
    private final @Nullable Double lowerTemperature;
    private final @Nullable Slots systemSlots;
    private final Slots userSlots;

    PromptName(
            final String resourceBaseName,
            final CallKind callKind,
            final String responseFormatName,
            final String responseSchema,
            final double temperature,
            @Nullable final Double lowerTemperature,
            @Nullable final Slots systemSlots,
            final Slots userSlots) {
        this.resourceBaseName = resourceBaseName;
        this.callKind = callKind;
        this.responseFormatName = responseFormatName;
        this.responseSchema = responseSchema;
        this.temperature = temperature;
        this.lowerTemperature = lowerTemperature;
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

    /** The user slots of the batch draft, kept out of the constant so the enum body stays readable. */
    private static final class BatchSlots {

        static final Slots USER = new Slots(
                Set.of("items", "source", "target"),
                Set.of(
                        "summary",
                        "glossaryTerms",
                        "lockedNames",
                        "suggestedTerms",
                        "memoryHint",
                        "lexiconTerms",
                        "keyTerms",
                        "characters",
                        "precedingPairs",
                        "nextSource",
                        "itemTokens",
                        "extraInstruction"));
    }

    /**
     * The slots one template file may use.
     *
     * @param required slots the file must contain as {@code {{slot}}}
     * @param optional slots the file may contain, alone or as a {@code {{#slot}}} block dropped when empty
     */
    public record Slots(Set<String> required, Set<String> optional) {

        /** Copies both sets so a declaration can never change after construction. */
        public Slots {
            required = Set.copyOf(Objects.requireNonNull(required, "required"));
            optional = Set.copyOf(Objects.requireNonNull(optional, "optional"));
        }

        boolean declares(final String slot) {
            return required.contains(slot) || optional.contains(slot);
        }
    }
}
