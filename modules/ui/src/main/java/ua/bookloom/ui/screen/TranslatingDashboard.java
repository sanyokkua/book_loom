package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/**
 * The built translating screen, and the two things about it that change with events rather than with a bound
 * property: the banner that says where the run is, and the log's scroll position.
 *
 * <p>The banner is one node whose words and role class are replaced, not one node per state, so that a state change
 * moves nothing on screen. Its role is never the only carrier of meaning: each state also has its own glyph and title.
 * A notice, when there is one, is worded in the same banner in place of the plain state; only a provider failure
 * shows the button that opens the provider settings.
 */
final class TranslatingDashboard {

    private static final String INFO = "banner-info";
    private static final String OK = "banner-ok";
    private static final String WARN = "banner-warn";
    private static final String ERR = "banner-err";
    private static final List<String> ROLE_CLASSES = List.of(INFO, OK, WARN, ERR);
    private static final int SECONDS_PER_MINUTE = 60;

    /** How the banner is told: its glyph, its role class, its words and whether it offers the provider settings. */
    private record Look(String glyph, String roleClass, String title, String text, boolean offersSettings) {}

    /** The nodes of the banner, which {@link #render(RunState, RunNotice, int)} rewrites. */
    record Banner(HBox box, Label icon, Label title, Label text, Button settings) {}

    private final Node root;
    private final Banner banner;
    private final ListView<LogEntry> log;
    private final Messages messages;
    private boolean scrollQueued;

    TranslatingDashboard(final Node root, final Banner banner, final ListView<LogEntry> log, final Messages messages) {
        this.root = Objects.requireNonNull(root, "root");
        this.banner = Objects.requireNonNull(banner, "banner");
        this.log = Objects.requireNonNull(log, "log");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    Node root() {
        return root;
    }

    /**
     * Words the banner from the notice when there is one, else from the run state, else from the wait.
     *
     * <p>A wait is worded only while the run is {@link RunState#RUNNING} and no notice is shown: a pending pause or
     * stop, a pause and every end are about the person's request or the outcome, not about the model being slow.
     *
     * @param state where the run is
     * @param notice what to say instead of the plain state, or {@code null} for the plain state
     * @param waitingSeconds how long the outstanding request has waited, or {@link StateMirror#NOT_WAITING}
     */
    void render(final RunState state, final @Nullable RunNotice notice, final int waitingSeconds) {
        Objects.requireNonNull(state, "state");
        final Look look = lookOf(state, notice, waitingSeconds);
        banner.icon().setText(look.glyph());
        banner.title().setText(look.title());
        banner.text().setText(look.text());
        banner.box().getStyleClass().removeAll(ROLE_CLASSES);
        banner.box().getStyleClass().add(look.roleClass());
        banner.settings().setVisible(look.offersSettings());
        banner.settings().setManaged(look.offersSettings());
    }

    /**
     * Brings the newest entry into view once the list has caught up with the change that announced it. The list's own
     * skin listens to the same items after this class's owner did, so scrolling at once would aim at the old size; the
     * scroll is therefore queued, and one queued scroll serves every change that arrives before it runs.
     */
    void scrollToNewest() {
        if (scrollQueued) {
            return;
        }
        scrollQueued = true;
        Platform.runLater(() -> {
            scrollQueued = false;
            final int last = log.getItems().size() - 1;
            if (last >= 0) {
                log.scrollTo(last);
            }
        });
    }

    private Look lookOf(final RunState state, final @Nullable RunNotice notice, final int waitingSeconds) {
        if (notice != null) {
            return lookOf(notice);
        }
        final Look plain = lookOf(state);
        if (state != RunState.RUNNING || waitingSeconds == StateMirror.NOT_WAITING) {
            return plain;
        }
        return new Look(
                plain.glyph(),
                plain.roleClass(),
                plain.title(),
                messages.get(MessageKey.TRANSLATING_WAITING_FOR_MODEL, clock(waitingSeconds)),
                false);
    }

    private static String clock(final int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
    }

    private Look lookOf(final RunNotice notice) {
        return switch (notice) {
            case RunNotice.ProviderError provider ->
                new Look(
                        "⛔",
                        ERR,
                        messages.get(
                                MessageKey.TRANSLATING_PROVIDER_TITLE,
                                provider.error().code().name()),
                        provider.error().message(),
                        true);
            case RunNotice.Refused refused ->
                new Look(
                        "⚠",
                        WARN,
                        messages.get(MessageKey.TRANSLATING_REFUSED_TITLE),
                        refused.error().message(),
                        false);
            case RunNotice.MissingInput missing ->
                new Look(
                        "⚠",
                        WARN,
                        messages.get(MessageKey.TRANSLATING_MISSING_TITLE),
                        messages.get(
                                MessageKey.TRANSLATING_MISSING_TEXT,
                                missing.which().token()),
                        false);
        };
    }

    private Look lookOf(final RunState state) {
        return switch (state) {
            case IDLE -> stateLook("ℹ", INFO, "idle");
            case RUNNING -> stateLook("▶", INFO, "running");
            case PAUSING -> stateLook("⏸", INFO, "pausing");
            case PAUSED -> stateLook("⏸", INFO, "paused");
            case STOPPING -> stateLook("⏹", INFO, "stopping");
            case STOPPED -> stateLook("⏹", INFO, "stopped");
            case COMPLETED -> stateLook("✓", OK, "completed");
            case FAILED -> stateLook("⚠", WARN, "failed");
        };
    }

    private Look stateLook(final String glyph, final String roleClass, final String token) {
        return new Look(
                glyph,
                roleClass,
                messages.get(MessageKey.TRANSLATING_STATE_TITLE, token),
                messages.get(MessageKey.TRANSLATING_STATE_TEXT, token),
                false);
    }
}
