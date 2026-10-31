package ua.bookloom.api.llm;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * How requests to one model are shaped: the half of a model's handling a request needs. Resolved from the server's
 * engine, the server's facts, the built-in row the model's name picks and the person's overrides, at every call; never
 * stored. What a person reads about the model is the {@link ModelReport}, which this record never carries.
 *
 * @param reasoning how the model's thinking is switched; non-null
 * @param markupFilters the reasoning markup a reply may hold beyond the common markers; never null, may be empty
 * @param systemRole whether the system text goes in a message of its own; non-null
 * @param quirks the template behaviours a request works around; never null, may be empty
 * @param sampling where the samplers come from: the profile's own, or the person's values; non-null
 * @param thinking the person's thinking choice for this model; non-null
 * @param context the window facts the requests are sized by; non-null
 * @param templateOverhead the tokens the model's chat template adds to every request; zero or more
 * @param batchSize the most segments one draft call carries; positive
 * @param tier the size class the context budgets are chosen by; non-null
 */
public record ModelProfile(
        ReasoningControl reasoning,
        Set<MarkupFilter> markupFilters,
        SystemRole systemRole,
        Set<Quirk> quirks,
        SamplingOverride sampling,
        ThinkingOverride thinking,
        ContextPolicy context,
        int templateOverhead,
        int batchSize,
        Tier tier) {

    /** The window a model with no row is held to: BookLoom's own default window. */
    public static final int DEFAULT_HARD_CAP = 16_384;

    /** The longest answer asked of a model with no row. */
    public static final int DEFAULT_OUTPUT_MAX = 8_192;

    /** The batch of a model with no row: small, so a model that loses items loses few. */
    public static final int DEFAULT_BATCH_SIZE = 2;

    /** Reasoning markup that a few models write in a form of their own. */
    public enum MarkupFilter {
        /** A "note to self" block before the answer. */
        TO_SELF,
        /** The answer wrapped in a boxed expression. */
        BOXED
    }

    /** Where the system text goes. */
    public enum SystemRole {
        /** A system message of its own. */
        NATIVE,
        /** Folded into the first user message, for a template without a system role. */
        FOLD
    }

    /** A template behaviour a request works around. */
    public enum Quirk {
        /** Thinking toggles written into the text are escaped. */
        ESCAPE_THINK_TOGGLES,
        /** No line names a reasoning level, strength or effort. */
        NO_REASONING_WORDS,
        /** A short string is checked again after the reply. */
        SHORT_STRING_CHECK,
        /** Structured answers start on the prompt-only rung instead of a schema. */
        PROMPT_ONLY_START,
        /** No worked examples are sent. */
        EXAMPLES_OFF,
        /** No hint about what the model may know is sent. */
        NO_KNOWLEDGE_HINT,
        /** A schema string reply that stops at a straight quote is asked again without the schema. */
        QUOTE_CUTS_SCHEMA_STRING,
        /** The chat template writes the reasoning level itself. */
        TEMPLATE_WRITES_REASONING,
        /** The harmony message format: the level goes only through the engine's reasoning field. */
        HARMONY_FORMAT
    }

    /** The size class of a model, which chooses its context budgets. */
    public enum Tier {
        /** A small model with tight budgets. */
        SMALL,
        /** A large model with wide budgets. */
        LARGE
    }

    public ModelProfile {
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(systemRole, "systemRole");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(thinking, "thinking");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tier, "tier");
        markupFilters = Set.copyOf(Objects.requireNonNull(markupFilters, "markupFilters"));
        quirks = Set.copyOf(Objects.requireNonNull(quirks, "quirks"));
        if (templateOverhead < 0) {
            throw new IllegalArgumentException("templateOverhead must not be negative: " + templateOverhead);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
    }

    /**
     * The safe profile of a model nothing is known about: no thinking control, answers asked through the prompt rather
     * than a schema, BookLoom's own temperatures, a batch of {@value #DEFAULT_BATCH_SIZE}.
     *
     * @return the default profile, with no window facts
     */
    public static ModelProfile defaults() {
        return new ModelProfile(
                ReasoningControl.NONE,
                Set.of(),
                SystemRole.NATIVE,
                EnumSet.of(Quirk.PROMPT_ONLY_START),
                SamplingOverride.profileDefault(),
                ThinkingOverride.RECOMMENDED,
                new ContextPolicy(DEFAULT_HARD_CAP, DEFAULT_OUTPUT_MAX, null, null),
                0,
                DEFAULT_BATCH_SIZE,
                Tier.SMALL);
    }
}
