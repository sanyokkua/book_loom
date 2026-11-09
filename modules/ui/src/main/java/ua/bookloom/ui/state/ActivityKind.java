package ua.bookloom.ui.state;

import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The kinds of work that talk to the model or to the provider, and which of them may not run beside each other.
 *
 * <p>The local model serves one request at a time, so every kind that asks it for text (the run, the names' and terms'
 * scan, review and translate, a review retry, the provider's inference test) is <em>exclusive</em>: one excludes every
 * other. The light provider checks (connection, model list) and the model listing ask the server, not the model, so
 * they may run during a run; any kind still excludes a second of its own kind. {@link #EXPORT} asks no model, but it is
 * exclusive too, because the export is not allowed while a translation is at work. The relation is symmetric by
 * construction ({@link #conflictsWith}).
 */
public enum ActivityKind {

    /** A translation run while it is translating (running, pausing or stopping), not while it waits paused. */
    TRANSLATION(
            MessageKey.ACTIVITY_TRANSLATION,
            ViewNames.TRANSLATING,
            new Behavior(true, false, false, true, false, false)),
    /** The glossary's model scan for names; abandoned work if the person leaves Names &amp; style. */
    GLOSSARY_SCAN(
            MessageKey.ACTIVITY_GLOSSARY_SCAN,
            ViewNames.NAMES_STYLE,
            new Behavior(true, true, true, true, false, false)),
    /** The glossary's model review of its rows; abandoned work if the person leaves Names &amp; style. */
    GLOSSARY_REVIEW(
            MessageKey.ACTIVITY_GLOSSARY_REVIEW,
            ViewNames.NAMES_STYLE,
            new Behavior(true, true, true, true, false, false)),
    /** The glossary's translate-only action; abandoned work if the person leaves Names &amp; style. */
    GLOSSARY_TRANSLATE(
            MessageKey.ACTIVITY_GLOSSARY_TRANSLATE,
            ViewNames.NAMES_STYLE,
            new Behavior(true, true, true, true, false, false)),
    /** The recurring terms' scan (the text first, then the model); abandoned work if the person leaves the screen. */
    TERM_SCAN(MessageKey.ACTIVITY_TERM_SCAN, ViewNames.NAMES_STYLE, new Behavior(true, true, true, true, false, false)),
    /** The recurring terms' model review; abandoned work if the person leaves Names &amp; style. */
    TERM_REVIEW(
            MessageKey.ACTIVITY_TERM_REVIEW, ViewNames.NAMES_STYLE, new Behavior(true, true, true, true, false, false)),
    /** The model's suggestion of a rendering for each recurring term; abandoned work if the person leaves the screen. */
    TERM_TRANSLATE(
            MessageKey.ACTIVITY_TERM_TRANSLATE,
            ViewNames.NAMES_STYLE,
            new Behavior(true, true, true, true, false, false)),
    /** The autonomous names pass over both tables of Names &amp; style; abandoned work if the person leaves the screen. */
    NAMES_PASS(
            MessageKey.ACTIVITY_NAMES_PASS, ViewNames.NAMES_STYLE, new Behavior(true, true, true, true, true, false)),
    /** One segment re-translated from the review panel. */
    REVIEW_RETRY(
            MessageKey.ACTIVITY_REVIEW_RETRY,
            ViewNames.TRANSLATING,
            new Behavior(true, false, true, true, false, false)),
    /** The provider test that asks the chosen model for a short answer. */
    PROVIDER_INFERENCE_TEST(
            MessageKey.ACTIVITY_PROVIDER_INFERENCE_TEST,
            ViewNames.SETTINGS,
            new Behavior(true, true, true, true, false, true)),
    /** The provider tests that ask only whether the server answers and lists the model. */
    PROVIDER_CHECK(
            MessageKey.ACTIVITY_PROVIDER_CHECK,
            ViewNames.SETTINGS,
            new Behavior(false, false, false, false, false, false)),
    /** Reading the provider's model list for the settings. */
    MODEL_LISTING(
            MessageKey.ACTIVITY_MODEL_LISTING,
            ViewNames.SETTINGS,
            new Behavior(false, false, false, false, false, false)),
    /** Checking and writing the translated book; it asks no model and cannot be hidden. */
    EXPORT(MessageKey.ACTIVITY_EXPORT, ViewNames.EXPORT, new Behavior(true, false, true, false, false, false)),
    /** The model's proposal of the translated book's file name. */
    SUGGEST_NAME(
            MessageKey.ACTIVITY_SUGGEST_NAME, ViewNames.EXPORT, new Behavior(true, false, true, true, false, false)),
    /** The model's proposal of the Book Brief's tone and style. */
    SUGGEST_STYLE(
            MessageKey.ACTIVITY_SUGGEST_STYLE,
            ViewNames.BOOK_BRIEF,
            new Behavior(true, false, true, true, false, false)),
    /** Opening a book and inspecting it, before the import screen has anything to show. */
    IMPORT(MessageKey.ACTIVITY_IMPORT, ViewNames.IMPORT, new Behavior(false, false, true, false, false, false)),
    /** The work between pressing Start and the first segment: style sheet, name scan, chunking. */
    RUN_PREPARATION(
            MessageKey.ACTIVITY_RUN_PREPARATION,
            ViewNames.TRANSLATING,
            new Behavior(false, false, true, true, false, false));

    private final MessageKey label;
    private final boolean exclusive;
    private final boolean leaveSensitive;
    private final ViewNames home;
    private final boolean blocking;
    private final boolean callingModel;
    private final boolean hideable;
    private final boolean ownCard;

    /**
     * How an activity behaves while it runs.
     *
     * @param exclusive whether it refuses a second activity beside it
     * @param leaveSensitive whether leaving its screen deserves a question
     * @param blocking whether the window waits for it behind a progress card
     * @param callingModel whether it asks the model
     * @param hideable whether its progress dialog may be put in the background
     * @param ownCard whether it shows its progress in a card of its own
     */
    private record Behavior(
            boolean exclusive,
            boolean leaveSensitive,
            boolean blocking,
            boolean callingModel,
            boolean hideable,
            boolean ownCard) {}

    ActivityKind(final MessageKey label, final ViewNames home, final Behavior behavior) {
        this.label = label;
        this.home = home;
        this.exclusive = behavior.exclusive();
        this.leaveSensitive = behavior.leaveSensitive();
        this.blocking = behavior.blocking();
        this.callingModel = behavior.callingModel();
        this.hideable = behavior.hideable();
        this.ownCard = behavior.ownCard();
    }

    /**
     * Whether the work shows its progress in a card of its own, so the work layer opens no progress dialog for it: the
     * provider's model tests show the request, the wait and the reply in their test card, or only the line beside
     * their button. It still blocks: navigation stays locked and closing the window asks.
     *
     * @return {@code true} for the provider's model tests, {@code false} otherwise
     */
    public boolean hasOwnCard() {
        return ownCard;
    }

    /**
     * Whether its progress dialog may be put in the background with "Continue in background": only long work, which
     * a person should not have to watch for minutes. Short work ends before anyone could want to hide it.
     *
     * @return {@code true} for the names pass, {@code false} otherwise
     */
    public boolean isHideable() {
        return hideable;
    }

    /**
     * Whether the window waits for this work behind a card with its progress: navigation and the actions that would
     * change what it works on are refused while it runs. The translation run itself, the model listing and the light
     * provider checks do not block, so the person can pause, browse and edit around them.
     *
     * @return {@code true} for work that changes or reads the book in one piece, {@code false} otherwise
     */
    public boolean isBlocking() {
        return blocking;
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
     * Whether the work asks the model for text, so the computer must not sleep while it runs.
     *
     * @return {@code true} for work that sends the model a request, {@code false} otherwise
     */
    public boolean isCallingModel() {
        return callingModel;
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
