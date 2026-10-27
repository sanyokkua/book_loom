package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * One row of the run-state table (README "Run states", design D8): what the title bar, the AI-model chip, the
 * toolbar, the pinned banner and the live line say in the state a project is in.
 *
 * <p>It is a pure function of {@link RunStateInputs} and holds no actions of its own: each surface reads the row and
 * keeps its own commands, addressed by the action ids below, so no surface can disagree with another about the state.
 *
 * @param row which of the sixteen states this is
 * @param title the title bar's state text
 * @param chip the AI-model chip
 * @param titleControl the title bar's one run control, or {@code null} where the row offers none
 * @param toolbar the run actions of the toolbar, left to right
 * @param banner the pinned banner, or {@code null} where the row has none
 * @param liveLine the Translating screen's line about the current work
 */
public record RunStateView(
        RunRow row,
        Text title,
        Chip chip,
        @Nullable RunAction titleControl,
        List<RunAction> toolbar,
        @Nullable BannerView banner,
        Text liveLine) {

    /** Starts a run, or continues a reopened, failed or finished one with a new run. */
    public static final String START = "translating-start";
    /** Pauses the run. */
    public static final String PAUSE = "translating-pause";
    /** Resumes a paused run, or continues a stopped one. */
    public static final String RESUME = "translating-resume";
    /** Asks before stopping the run. */
    public static final String STOP = "translating-stop";
    /** Opens the review desk. */
    public static final String REVIEW = "translating-review-flagged";
    /** Shows Export. */
    public static final String TO_EXPORT = "translating-continue";
    /** Sends the interrupted request again now. */
    public static final String RETRY = "translating-retry-now";
    /** Flags the segments in flight and goes on. */
    public static final String SKIP = "translating-skip-segment";
    /** Opens the provider settings. */
    public static final String OPEN_SETTINGS = "translating-open-settings";
    /** Opens the provider settings to choose another model. */
    public static final String CHOOSE_MODEL = "translating-choose-model";
    /** Opens the failure of a failed run. */
    public static final String SHOW_DETAILS = "translating-show-details";
    /** Opens the review desk from a finished run's banner. */
    public static final String REVIEW_SEGMENTS = "translating-review-segments";
    /** Shows Translating from a banner on another screen. */
    public static final String GO_TRANSLATING = "translating-go-translating";
    /** Shows the Book Brief, where a missing target language is chosen. */
    public static final String GO_BRIEF = "translating-go-brief";

    /** The value a text argument takes when it is not known, which the catalogue's {@code select} words. */
    static final String NONE = "none";

    /** Rejects a missing part and freezes the toolbar. */
    public RunStateView {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(chip, "chip");
        toolbar = List.copyOf(Objects.requireNonNull(toolbar, "toolbar"));
        Objects.requireNonNull(liveLine, "liveLine");
    }

    /**
     * The row for what is known about the run.
     *
     * @param in the inputs
     * @return the view; never null
     */
    public static RunStateView of(final RunStateInputs in) {
        final RunRow row = RunRow.of(in);
        return new RunStateView(
                row,
                title(row, in),
                RunChips.of(row, in),
                RunToolbar.titleControl(row, in),
                RunToolbar.of(row, in),
                RunBanners.of(row, in),
                RunChips.liveLine(row, in));
    }

    /**
     * Whether BookLoom has a request out or sends one by itself, so every other model action waits.
     *
     * @return {@code true} in the rows that are at work, {@code false} otherwise
     */
    public boolean isAtWork() {
        return row.isAtWork();
    }

    private static Text title(final RunRow row, final RunStateInputs in) {
        return switch (row) {
            case READY, CANNOT_START -> Text.of(MessageKey.RUN_TITLE_READY);
            case READY_TO_CONTINUE -> Text.of(MessageKey.RUN_TITLE_READY_CONTINUE, in.percent());
            case TRANSLATING, STUCK -> Text.of(MessageKey.RUN_TITLE_RUNNING, in.percent(), orNone(in.timeLeft()));
            case PAUSING -> Text.of(MessageKey.RUN_TITLE_PAUSING);
            case WAITING -> Text.of(MessageKey.RUN_TITLE_WAITING, in.model().provider());
            case PAUSED, PAUSED_AFTER_ERROR, STOPPED_TRYING, MODEL_MISSING ->
                Text.of(MessageKey.RUN_TITLE_PAUSED, in.percent());
            case STOPPING -> Text.of(MessageKey.RUN_TITLE_STOPPING);
            case STOPPED -> Text.of(MessageKey.RUN_TITLE_STOPPED, in.percent());
            case FINISHED_TO_REVIEW, FINISHED_NONE -> Text.of(MessageKey.RUN_TITLE_FINISHED);
            case FAILED -> Text.of(MessageKey.RUN_TITLE_FAILED);
        };
    }

    static Object orNone(final @Nullable String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    static String clock(final Duration duration) {
        return DurationText.clock(Math.toIntExact(Math.max(0, duration.toSeconds())));
    }

    /**
     * A text from the catalogue with its arguments; an argument that is itself a {@link MessageKey} is worded first.
     *
     * @param key the catalogue entry
     * @param args its arguments
     */
    public record Text(MessageKey key, List<Object> args) {

        /** Rejects a missing part and freezes the arguments. */
        public Text {
            Objects.requireNonNull(key, "key");
            args = List.copyOf(Objects.requireNonNull(args, "args"));
        }

        /**
         * A text with these arguments.
         *
         * @param key the catalogue entry
         * @param args its arguments, none of them null
         * @return the text
         */
        public static Text of(final MessageKey key, final Object... args) {
            return new Text(key, Arrays.asList(args));
        }

        /**
         * The text in the catalogue's language.
         *
         * @param messages the catalogue
         * @return the worded text
         */
        public String in(final Messages messages) {
            Objects.requireNonNull(messages, "messages");
            final Object[] worded =
                    args.stream().map(arg -> worded(messages, arg)).toArray();
            return messages.get(key, worded);
        }

        // A failure argument is worded in the display language (ErrorWords): its own words are English.
        private static Object worded(final Messages messages, final Object arg) {
            return switch (arg) {
                case MessageKey nested -> messages.get(nested);
                case AppError failure -> ErrorWords.message(messages, failure);
                default -> arg;
            };
        }
    }

    /**
     * The AI-model chip: one of the state markers' meanings and its word.
     *
     * @param meaning its colour and icon
     * @param word what it says
     */
    public record Chip(StateMarker.Meaning meaning, Text word) {

        /** Rejects a missing part. */
        public Chip {
            Objects.requireNonNull(meaning, "meaning");
            Objects.requireNonNull(word, "word");
        }
    }

    /**
     * A run action as one surface shows it.
     *
     * @param id the action's node id, which also names the command the surface runs
     * @param label what the button says
     * @param primary whether it is the row's main action
     * @param enabled whether it can be pressed now
     * @param tip the hover explanation; for an action held by other work, why it waits
     */
    public record RunAction(String id, Text label, boolean primary, boolean enabled, Text tip) {

        /** Rejects a missing part. */
        public RunAction {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tip, "tip");
        }
    }

    /**
     * The pinned banner of a row.
     *
     * @param key the row's banner name ("waiting"), stable for tests and the slot
     * @param role its colour role
     * @param glyph the mark before the words
     * @param title the first line
     * @param text the second line, or {@code null}
     * @param actions its actions, left to right
     * @param translatingOnly whether it shows on the Translating screen only (a finished run's banner)
     */
    public record BannerView(
            String key,
            Tone role,
            String glyph,
            Text title,
            @Nullable Text text,
            List<RunAction> actions,
            boolean translatingOnly) {

        /** Rejects a missing part and freezes the actions. */
        public BannerView {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(glyph, "glyph");
            Objects.requireNonNull(title, "title");
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        }
    }
}
