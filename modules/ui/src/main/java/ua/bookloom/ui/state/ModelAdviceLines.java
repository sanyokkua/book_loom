package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageDeclaration;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelNotes;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Words the advice of one {@link AdviceReading} for the Models tab and Start. Pure: it reads the reading and the
 * catalogue and holds no state, so every line is the same wherever it is shown.
 */
@Slf4j
public final class ModelAdviceLines {

    /** A line with the tone it is drawn in. */
    public record Line(Tone tone, String text) {

        public Line {
            Objects.requireNonNull(tone, "tone");
            Objects.requireNonNull(text, "text");
        }
    }

    private static final String SEPARATOR = " · ";
    private static final String ENGLISH = "en";
    private static final List<String> FIRST_CODES = List.of("uk", "ru");
    private static final int SOME_CODES = 3;
    private static final double SMALL_MODEL_BILLIONS = 5.0;
    private static final int RANK_NOT_DECLARED = 2;
    private static final int RANK_UNKNOWN = 1;

    private final Messages messages;
    private final LanguageNames names;

    /**
     * Creates the builder.
     *
     * @param messages the catalogue the lines are worded from
     * @param names how a language tag is named in the interface language
     */
    public ModelAdviceLines(final Messages messages, final LanguageNames names) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.names = Objects.requireNonNull(names, "names");
    }

    /**
     * The identity line: model, quant and how the name was recognised.
     *
     * @param reading the reading to word
     * @return the line, or empty when the name was not read
     */
    public Optional<String> identity(final AdviceReading reading) {
        final ModelReport report = reading.report();
        final ModelNotes notes = report.notes();
        final ModelIdentity identity = report.identity();
        if (notes == null || identity == null) {
            log.debug("identity line skipped: the report has no notes or no identity");
            return Optional.empty();
        }
        final List<String> parts = Stream.of(notes.label(), quantPart(identity, notes), matchPart(report, notes))
                .filter(part -> !part.isEmpty())
                .toList();
        return Optional.of(messages.get(MessageKey.SETTINGS_ADVICE_IDENTITY, String.join(SEPARATOR, parts)));
    }

    private String quantPart(final ModelIdentity identity, final ModelNotes notes) {
        final String named = identity.quantLabel();
        final String served = notes.serverQuant();
        if (named != null && served != null && !named.equalsIgnoreCase(served)) {
            return messages.get(MessageKey.SETTINGS_ADVICE_IDENTITY_SERVER, named, served);
        }
        if (named != null) {
            return named;
        }
        return served == null ? "" : served;
    }

    // "A fine-tune of" only where the handling really is the base model's (BASE_FAMILY); any other row is the model's.
    private String matchPart(final ModelReport report, final ModelNotes notes) {
        final String base = report.baseModel();
        return switch (report.match()) {
            case OVERRIDE, EXACT -> messages.get(MessageKey.SETTINGS_ADVICE_MATCH_EXACT);
            case BASE_FAMILY ->
                base == null
                        ? messages.get(MessageKey.SETTINGS_ADVICE_MATCH_FAMILY, notes.familyLabel())
                        : messages.get(MessageKey.SETTINGS_ADVICE_MATCH_FINE_TUNE, base);
            case SIZE, FAMILY_VERSION, FAMILY, ARCHITECTURE ->
                notes.familyLabel().isEmpty()
                        ? messages.get(MessageKey.SETTINGS_ADVICE_MATCH_UNKNOWN)
                        : messages.get(MessageKey.SETTINGS_ADVICE_MATCH_FAMILY, notes.familyLabel());
            case DEFAULT -> messages.get(MessageKey.SETTINGS_ADVICE_MATCH_UNKNOWN);
        };
    }

    /**
     * The sentence for the model's quant tier.
     *
     * @param reading the reading to word
     * @param atStart whether the line is the one at Start, which is shown only for a quant that needs it and never
     *     for a model BookLoom measured at that quant
     * @return the sentence, or empty for an excellent or unjudged quant (and, at Start, one that needs no line)
     */
    public Optional<String> quant(final AdviceReading reading, final boolean atStart) {
        final QuantAdvice advice = reading.report().quantAdvice();
        final QuantTier tier = advice == null ? null : advice.tier();
        if (advice == null || tier == null || tier == QuantTier.EXCELLENT) {
            return Optional.empty();
        }
        if (atStart && !needsStartLine(reading, tier)) {
            log.debug("quant line at Start skipped tier={}", tier);
            return Optional.empty();
        }
        final String clause = reading.targetTag() == null
                ? ""
                : messages.get(MessageKey.SETTINGS_ADVICE_ESPECIALLY, languageName(reading.targetTag()));
        return Optional.of(
                switch (tier) {
                    case GOOD -> messages.get(MessageKey.SETTINGS_ADVICE_QUANT_GOOD);
                    case ACCEPTABLE ->
                        cautious(advice)
                                ? messages.get(MessageKey.SETTINGS_ADVICE_QUANT_CAUTION)
                                : messages.get(MessageKey.SETTINGS_ADVICE_QUANT_ACCEPTABLE, clause);
                    case POOR -> messages.get(MessageKey.SETTINGS_ADVICE_QUANT_POOR, clause);
                    case UNUSABLE -> messages.get(MessageKey.SETTINGS_ADVICE_QUANT_UNUSABLE);
                    case REMOVED -> messages.get(MessageKey.SETTINGS_ADVICE_QUANT_REMOVED);
                    case UNMEASURED -> messages.get(MessageKey.SETTINGS_ADVICE_QUANT_UNMEASURED);
                    case EXCELLENT -> "";
                });
    }

    private static boolean cautious(final QuantAdvice advice) {
        return advice.bitClass() != null && advice.bitClass().startsWith("3");
    }

    private static boolean needsStartLine(final AdviceReading reading, final QuantTier tier) {
        final ModelNotes notes = reading.report().notes();
        if (notes != null && notes.quantMeasured()) {
            return false;
        }
        return switch (tier) {
            case POOR, UNUSABLE, REMOVED -> true;
            case ACCEPTABLE -> isSmall(reading.report().identity());
            case EXCELLENT, GOOD, UNMEASURED -> false;
        };
    }

    private static boolean isSmall(final @Nullable ModelIdentity identity) {
        if (identity == null) {
            return false;
        }
        final Double size = identity.effB() != null ? identity.effB() : identity.sizeB();
        return size != null && size <= SMALL_MODEL_BILLIONS;
    }

    /**
     * The neutral line saying what the maker declares, with BookLoom's own test where the pair has one.
     *
     * @param reading the reading to word
     * @return the line; never empty
     */
    public String languages(final AdviceReading reading) {
        final LanguageAdvice advice = reading.languages();
        final LanguageDeclaration declared = reading.report().languages();
        final String line;
        if (advice.baseLabel() != null) {
            line = messages.get(
                    MessageKey.SETTINGS_ADVICE_LANGUAGES_BASE, advice.baseLabel(), shown(advice.baseCodes()));
        } else if (declared == null || declared.listKind() == ListKind.NONE) {
            line = messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGES_NOT_STATED);
        } else if (declared.codes().equals(Set.of(ENGLISH))) {
            line = messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGES_ENGLISH_ONLY);
        } else {
            line = messages.get(
                    MessageKey.SETTINGS_ADVICE_LANGUAGES_LIST,
                    declared.codes().size(),
                    shown(ordered(declared.codes())));
        }
        final String source = reading.sourceTag();
        final String target = reading.targetTag();
        if (!advice.pairTested() || source == null || target == null) {
            final boolean listed =
                    advice.baseLabel() != null || (declared != null && declared.listKind() != ListKind.NONE);
            return listed ? withTarget(line, advice.targetState(), target) : line;
        }
        return messages.get(
                MessageKey.SETTINGS_ADVICE_LANGUAGES_TESTED, line, languageName(source), languageName(target));
    }

    // What a list says about the target: nothing can be checked while none is chosen (no book open), and a list says
    // "declared" or, only when it is complete, "not declared"; an unknown state adds nothing.
    private String withTarget(final String line, final LanguageState state, final @Nullable String target) {
        log.debug("languages line: target {} in state {}", target, state);
        if (target == null) {
            return messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGES_NO_TARGET, line);
        }
        return switch (state) {
            case DECLARED ->
                messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGES_TARGET_DECLARED, line, languageName(target));
            case NOT_DECLARED ->
                messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGES_TARGET_NOT_DECLARED, line, languageName(target));
            case UNKNOWN -> line;
        };
    }

    private static List<String> ordered(final Set<String> codes) {
        return Stream.concat(
                        FIRST_CODES.stream().filter(codes::contains),
                        codes.stream()
                                .filter(code -> !FIRST_CODES.contains(code))
                                .sorted())
                .toList();
    }

    // Ukrainian and Russian are the languages a reader of this tab looks for; any other list shows its first few.
    private static String shown(final List<String> orderedCodes) {
        final List<String> priority =
                orderedCodes.stream().filter(FIRST_CODES::contains).toList();
        return String.join(
                ", ",
                priority.isEmpty() ? orderedCodes.stream().limit(SOME_CODES).toList() : priority);
    }

    /**
     * The one language line for the side in the worse state, both on a tie: a warning where the maker's complete list
     * leaves a language out, an information line where the maker says nothing or only a base model lists it, and
     * nothing where the model declares the pair or BookLoom tested it.
     *
     * @param reading the reading to word
     * @return the line, or empty when there is nothing to say
     */
    public Optional<Line> languageLine(final AdviceReading reading) {
        final LanguageAdvice advice = reading.languages();
        if (advice.pairTested()) {
            log.debug("language line skipped: BookLoom tested the pair");
            return Optional.empty();
        }
        final int source = rank(advice.sourceState());
        final int target = rank(advice.targetState());
        final int worst = Math.max(source, target);
        final String family = familyOf(reading);
        if (worst == RANK_NOT_DECLARED) {
            final String missing = namesOf(reading, source == worst, target == worst);
            return Optional.of(
                    new Line(Tone.WARN, messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGE_WARN, family, missing)));
        }
        if (worst == RANK_UNKNOWN) {
            return Optional.of(new Line(Tone.INFO, messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGE_UNKNOWN, family)));
        }
        if (advice.baseOnly() && advice.baseLabel() != null) {
            final String listed = namesOf(reading, true, true);
            return Optional.of(new Line(
                    Tone.INFO,
                    messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGE_BASE, family, advice.baseLabel(), listed)));
        }
        return Optional.empty();
    }

    private static int rank(final LanguageState state) {
        return switch (state) {
            case NOT_DECLARED -> RANK_NOT_DECLARED;
            case UNKNOWN -> RANK_UNKNOWN;
            case DECLARED -> 0;
        };
    }

    private String familyOf(final AdviceReading reading) {
        final String family = reading.languages().familyLabel();
        if (!family.isBlank()) {
            return family;
        }
        final ModelNotes notes = reading.report().notes();
        return notes == null || notes.familyLabel().isBlank() ? reading.modelId() : notes.familyLabel();
    }

    private String namesOf(final AdviceReading reading, final boolean withSource, final boolean withTarget) {
        final List<String> tags = Stream.of(
                        withSource ? reading.sourceTag() : null, withTarget ? reading.targetTag() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        final List<String> shown = tags.stream().map(this::languageName).toList();
        return shown.size() == 2
                ? messages.get(MessageKey.SETTINGS_ADVICE_LANGUAGE_AND, shown.get(0), shown.get(1))
                : String.join("", shown);
    }

    private String languageName(final @Nullable String tag) {
        return tag == null ? "" : names.nameOf(tag, messages.locale());
    }

    /**
     * The licence note of a model that allows noncommercial use only.
     *
     * @param reading the reading to word
     * @return the note, or empty for an open licence
     */
    public Optional<String> licence(final AdviceReading reading) {
        final ModelReport report = reading.report();
        if (!report.warnings().contains(ModelReport.WARNING_LICENCE_NOTE)) {
            return Optional.empty();
        }
        final ModelNotes notes = report.notes();
        return Optional.of(
                messages.get(MessageKey.SETTINGS_ADVICE_LICENCE, notes == null ? reading.modelId() : notes.label()));
    }

    /**
     * The hint for a download that cannot turn thinking off.
     *
     * @param reading the reading to word
     * @return the hint, or empty when no library model fixes it
     */
    public Optional<String> thinking(final AdviceReading reading) {
        final ModelNotes notes = reading.report().notes();
        if (notes == null || notes.libraryTag() == null) {
            return Optional.empty();
        }
        // The port names a library model only for an Ollama provider, the one engine that has a library to fetch from.
        return Optional.of(messages.get(
                MessageKey.SETTINGS_ADVICE_THINKING_HINT, ServerEngine.OLLAMA.label(), notes.libraryTag()));
    }

    /**
     * The foot of the Models tab.
     *
     * @param reading the reading to word
     * @return the line, or empty when the shipped knowledge carries no date
     */
    public Optional<String> factsDate(final AdviceReading reading) {
        return reading.factsDate().isEmpty()
                ? Optional.empty()
                : Optional.of(messages.get(MessageKey.SETTINGS_ADVICE_FACTS_DATE, reading.factsDate()));
    }
}
