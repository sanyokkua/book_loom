package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What the Models tab holds while a person edits one model: every number as text, so a half-typed figure is still a
 * draft and only {@link #findings()} and {@link #apply} judge it.
 *
 * <p>The temperature is kept across a change of choice, so switching to "Let BookLoom choose" and back does not lose
 * it, and it takes part in equality only while the choice is {@code VALUE}.
 *
 * @param choice how the temperature is sent
 * @param temperature the value for {@code VALUE}, 0.0 to 2.0 in steps of 0.01
 * @param contextOn whether the context window is set; off means the detected one is used
 * @param contextText the window as typed; empty while off
 * @param limitOn whether the answer length is capped
 * @param limitText the cap as typed; empty while off
 * @param field the field that carries the cap on an OpenAI-compatible server
 */
public record ModelDraft(
        TemperatureChoice choice,
        double temperature,
        boolean contextOn,
        String contextText,
        boolean limitOn,
        String limitText,
        OutputLengthField field) {

    /** The temperature shown before the person has chosen one. */
    public static final double DEFAULT_TEMPERATURE = 0.2;

    private static final double HUNDRED = 100.0;

    public ModelDraft {
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(contextText, "contextText");
        Objects.requireNonNull(limitText, "limitText");
        Objects.requireNonNull(field, "field");
    }

    /**
     * The draft that shows stored settings.
     *
     * @param settings what is stored for the model
     * @return the draft that equals them
     */
    public static ModelDraft of(final ModelSettings settings) {
        Objects.requireNonNull(settings, "settings");
        final Integer context = settings.contextWindow();
        final Integer limit = settings.maxOutput();
        final Double temperature = settings.temperature();
        return new ModelDraft(
                settings.temperatureChoice(),
                temperature == null ? DEFAULT_TEMPERATURE : temperature,
                context != null,
                context == null ? "" : String.valueOf(context),
                limit != null,
                limit == null ? "" : String.valueOf(limit),
                settings.outputField());
    }

    /** The same draft with another temperature choice; the temperature is kept. */
    public ModelDraft withChoice(final TemperatureChoice next) {
        return new ModelDraft(next, temperature, contextOn, contextText, limitOn, limitText, field);
    }

    /** The same draft with another temperature, rounded to 0.01 and held within 0.0 to 2.0. */
    public ModelDraft withTemperature(final double next) {
        final double held = Math.max(ModelSettings.MIN_TEMPERATURE, Math.min(ModelSettings.MAX_TEMPERATURE, next));
        final double rounded = Math.round(held * HUNDRED) / HUNDRED;
        return new ModelDraft(choice, rounded, contextOn, contextText, limitOn, limitText, field);
    }

    /**
     * Switches the context window on or off.
     *
     * @param on whether the window is set
     * @param text the typed window; ignored while off
     * @param suggestion the window to start from when switched on with nothing typed, or null for none
     * @return the changed draft
     */
    public ModelDraft withContext(final boolean on, final String text, final @Nullable Integer suggestion) {
        Objects.requireNonNull(text, "text");
        final String kept;
        if (!on) {
            kept = "";
        } else if (text.isBlank() && suggestion != null) {
            kept = String.valueOf(suggestion);
        } else {
            kept = text;
        }
        return new ModelDraft(choice, temperature, on, kept, limitOn, limitText, field);
    }

    /** Switches the answer cap on or off; the text is ignored while off. */
    public ModelDraft withLimit(final boolean on, final String text) {
        Objects.requireNonNull(text, "text");
        return new ModelDraft(choice, temperature, contextOn, contextText, on, on ? text : "", field);
    }

    /** The same draft with another typed window. */
    public ModelDraft withContextText(final String text) {
        return new ModelDraft(choice, temperature, contextOn, text, limitOn, limitText, field);
    }

    /** The same draft with another typed cap. */
    public ModelDraft withLimitText(final String text) {
        return new ModelDraft(choice, temperature, contextOn, contextText, limitOn, text, field);
    }

    /** The same draft carrying the cap in another field. */
    public ModelDraft withField(final OutputLengthField next) {
        return new ModelDraft(choice, temperature, contextOn, contextText, limitOn, limitText, next);
    }

    /**
     * What stops a save.
     *
     * @return the messages for every switched-on number that is not a whole number above zero; empty when fine
     */
    public List<MessageKey> findings() {
        final boolean contextBad = contextOn && parse(contextText).isEmpty();
        final boolean limitBad = limitOn && parse(limitText).isEmpty();
        return contextBad || limitBad ? List.of(MessageKey.SETTINGS_MODELS_ERROR_NUMBER) : List.of();
    }

    /**
     * Writes the draft over stored settings, keeping everything it does not hold.
     *
     * @param current what is stored now
     * @return the settings to store, or a validation error while {@link #findings()} is not empty
     */
    public Result<ModelSettings> apply(final ModelSettings current) {
        Objects.requireNonNull(current, "current");
        if (!findings().isEmpty()) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Not a number", "A switched-on figure is not a whole number above zero."));
        }
        return Result.ok(current.withChoices(
                choice,
                choice == TemperatureChoice.VALUE ? temperature : null,
                contextOn ? parse(contextText).orElse(null) : null,
                limitOn ? parse(limitText).orElse(null) : null,
                field));
    }

    // The temperature only counts while it is the value sent.
    @Override
    public boolean equals(final @Nullable Object other) {
        return other instanceof ModelDraft that
                && choice == that.choice
                && (choice != TemperatureChoice.VALUE || Double.compare(temperature, that.temperature) == 0)
                && contextOn == that.contextOn
                && contextText.equals(that.contextText)
                && limitOn == that.limitOn
                && limitText.equals(that.limitText)
                && field == that.field;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                choice,
                choice == TemperatureChoice.VALUE ? temperature : 0.0,
                contextOn,
                contextText,
                limitOn,
                limitText,
                field);
    }

    private static Optional<Integer> parse(final String text) {
        try {
            final int value = Integer.parseInt(text.strip());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
