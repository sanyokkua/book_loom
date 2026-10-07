package ua.bookloom.ui.state;

import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The kinds of work that talk to the model or to the provider, and which of them may not run beside each other.
 *
 * <p>The local model serves one request at a time, so every kind that asks it for text — the run, the glossary's scan
 * and review, a review retry, the provider's inference test and an export (whose consistency pass may ask it) — is
 * <em>exclusive</em>: one excludes every other. The light provider checks (connection, model list) and the model
 * listing ask the server, not the model, so they may run during a run; any kind still excludes a second of its own
 * kind. The relation is symmetric by construction ({@link #conflictsWith}).
 */
public enum ActivityKind {

    /** A translation run while it is translating (running, pausing or stopping), not while it waits paused. */
    TRANSLATION(MessageKey.ACTIVITY_TRANSLATION, true, false, ViewNames.TRANSLATING),
    /** The glossary's model scan for names; abandoned work if the person leaves Names &amp; style. */
    GLOSSARY_SCAN(MessageKey.ACTIVITY_GLOSSARY_SCAN, true, true, ViewNames.NAMES_STYLE),
    /** The glossary's model review of its rows; abandoned work if the person leaves Names &amp; style. */
    GLOSSARY_REVIEW(MessageKey.ACTIVITY_GLOSSARY_REVIEW, true, true, ViewNames.NAMES_STYLE),
    /** One segment re-translated from the review panel. */
    REVIEW_RETRY(MessageKey.ACTIVITY_REVIEW_RETRY, true, false, ViewNames.TRANSLATING),
    /** The provider test that asks the chosen model for a short answer. */
    PROVIDER_INFERENCE_TEST(MessageKey.ACTIVITY_PROVIDER_INFERENCE_TEST, true, true, ViewNames.SETTINGS),
    /** The provider tests that ask only whether the server answers and lists the model. */
    PROVIDER_CHECK(MessageKey.ACTIVITY_PROVIDER_CHECK, false, false, ViewNames.SETTINGS),
    /** Reading the provider's model list for the settings. */
    MODEL_LISTING(MessageKey.ACTIVITY_MODEL_LISTING, false, false, ViewNames.SETTINGS),
    /** Writing the translated book, whose consistency pass may ask the model. */
    EXPORT(MessageKey.ACTIVITY_EXPORT, true, false, ViewNames.EXPORT),
    /** The model's proposal of the translated book's file name. */
    SUGGEST_NAME(MessageKey.ACTIVITY_SUGGEST_NAME, true, false, ViewNames.EXPORT),
    /** The model's proposal of the Book Brief's tone and style. */
    SUGGEST_STYLE(MessageKey.ACTIVITY_SUGGEST_STYLE, true, false, ViewNames.BOOK_BRIEF);

    private final MessageKey label;
    private final boolean exclusive;
    private final boolean leaveSensitive;
    private final ViewNames home;

    ActivityKind(final MessageKey label, final boolean exclusive, final boolean leaveSensitive, final ViewNames home) {
        this.label = label;
        this.exclusive = exclusive;
        this.leaveSensitive = leaveSensitive;
        this.home = home;
    }

    /**
     * How the activity is named in the title bar and in a reason a control is unavailable.
     *
     * @return the catalogue key of its short name
     */
    public MessageKey label() {
        return label;
    }

    /**
     * Whether leaving the activity's screen while it runs deserves a question, because its result is shown only there.
     *
     * @return {@code true} for the glossary's model actions and the provider's inference test, {@code false} otherwise
     */
    public boolean isLeaveSensitive() {
        return leaveSensitive;
    }

    /**
     * The screen the activity is started from and shows its result on.
     *
     * @return the screen; never null
     */
    public ViewNames home() {
        return home;
    }

    /**
     * Whether this kind may not run while {@code other} runs; the same answer either way round.
     *
     * @param other the kind already running
     * @return {@code true} if both are the same kind or both ask the model, {@code false} otherwise
     */
    public boolean conflictsWith(final ActivityKind other) {
        return this == other || (exclusive && other.exclusive);
    }
}
