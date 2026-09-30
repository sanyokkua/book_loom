package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.DurationText;
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

    private static final Banner.Role INFO = Banner.Role.INFO;
    private static final Banner.Role OK = Banner.Role.OK;
    private static final Banner.Role WARN = Banner.Role.WARN;
    private static final Banner.Role ERR = Banner.Role.ERR;

    /** How the banner is told: its glyph, its role class, its words and whether it offers the provider settings. */
    private record Look(String glyph, Banner.Role role, String title, String text, boolean offersSettings) {}

    /** The banner, which {@link #render(RunState, RunNotice, int)} rewrites, and its provider-settings action. */
    record LiveBanner(Banner banner, Button settings) {}

    private final Node root;
    private final LiveBanner banner;
    private final ListView<LogEntry> log;
    private final Messages messages;
    private final StateMirror mirror;
    private boolean scrollQueued;

    TranslatingDashboard(
            final Node root,
            final LiveBanner banner,
            final ListView<LogEntry> log,
            final Messages messages,
            final StateMirror mirror) {
        this.root = Objects.requireNonNull(root, "root");
        this.banner = Objects.requireNonNull(banner, "banner");
        this.log = Objects.requireNonNull(log, "log");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
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
        banner.banner().setGlyph(look.glyph());
        banner.banner().setTitle(look.title());
        banner.banner().setText(look.text());
        banner.banner().setRole(look.role());
        Banner.setActionShown(banner.settings(), look.offersSettings());
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
                plain.role(),
                plain.title(),
                messages.get(MessageKey.TRANSLATING_WAITING_FOR_MODEL, DurationText.clock(waitingSeconds)),
                false);
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
            case PAUSED -> pausedLook();
            case STOPPING -> stateLook("⏹", INFO, "stopping");
            case STOPPED -> stateLook("⏹", INFO, "stopped");
            case COMPLETED -> stateLook("✓", OK, "completed");
            case FAILED -> stateLook("⚠", WARN, "failed");
        };
    }

    // The chunk the run continues at is named once it is known; before that the plain paused text stands.
    private Look pausedLook() {
        if (mirror.chunks().get() == 0) {
            return stateLook("⏸", INFO, "paused");
        }
        return new Look(
                "⏸",
                INFO,
                messages.get(MessageKey.TRANSLATING_STATE_TITLE, "paused"),
                messages.get(
                        MessageKey.TRANSLATING_PAUSED_TEXT,
                        mirror.chunk().get(),
                        mirror.chunks().get()),
                false);
    }

    private Look stateLook(final String glyph, final Banner.Role role, final String token) {
        return new Look(
                glyph,
                role,
                messages.get(MessageKey.TRANSLATING_STATE_TITLE, token),
                messages.get(MessageKey.TRANSLATING_STATE_TEXT, token),
                false);
    }
}
