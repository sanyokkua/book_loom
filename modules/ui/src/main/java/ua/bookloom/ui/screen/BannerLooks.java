package ua.bookloom.ui.screen;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.PauseNotice;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.WaitingCall;

/**
 * Words the translating banner: from a notice when there is one, else from the run state, else from the request the
 * run waits on. Holds no state; the dashboard asks it for a look on every redraw, so it logs nothing.
 */
@Slf4j
final class BannerLooks {

    private static final Banner.Role INFO = Banner.Role.INFO;
    private static final Banner.Role OK = Banner.Role.OK;
    private static final Banner.Role WARN = Banner.Role.WARN;
    private static final Banner.Role ERR = Banner.Role.ERR;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final String NONE = "none";

    /** Which of the banner's actions a look offers. */
    enum Offer {
        /** No action. */
        NONE,
        /** Only the route to the provider settings, for a failed preparation. */
        SETTINGS_ONLY,
        /** Retry now, Skip segment, the settings and Stay paused, for a run paused on a provider error. */
        PROVIDER,
        /** Skip segment, Retry now and Pause, for a request that has waited too long. */
        STUCK
    }

    /** How the banner is told: its glyph, its role class, its words and which actions it offers. */
    record Look(String glyph, Banner.Role role, String title, String text, Offer offer) {}

    private final Messages messages;
    private final StateMirror mirror;

    BannerLooks(final Messages messages, final StateMirror mirror) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
    }

    /**
     * The look for what the run shows now.
     *
     * @param state where the run is
     * @param notice what to say instead of the plain state, or {@code null}
     * @param waitingSeconds how long the outstanding request has waited, or {@link StateMirror#NOT_WAITING}
     * @param waiting the request the run waits on, or {@code null} when only its wait is known; worded only while
     *     running with no notice
     * @param offerProvider whether a provider error's actions are still offered (Stay paused withdraws them)
     * @return the look
     */
    Look lookOf(
            final RunState state,
            final @Nullable RunNotice notice,
            final int waitingSeconds,
            final @Nullable WaitingCall waiting,
            final boolean offerProvider) {
        if (notice != null) {
            return noticeLook(state, notice, offerProvider);
        }
        final Look plain = stateLook(state);
        if (state != RunState.RUNNING || waitingSeconds == StateMirror.NOT_WAITING) {
            return plain;
        }
        return waitingLook(plain, waitingSeconds, waiting);
    }

    // Only the wait's length is known before the request's own announcement reaches the screen.
    private Look waitingLook(final Look plain, final int waitingSeconds, final @Nullable WaitingCall waiting) {
        if (waiting == null) {
            final String text =
                    messages.get(MessageKey.TRANSLATING_WAITING_FOR_MODEL, clock(Duration.ofSeconds(waitingSeconds)));
            return new Look(plain.glyph(), plain.role(), plain.title(), text, Offer.NONE);
        }
        final String line = waitingLine(waiting);
        if (waiting.stuck()) {
            return new Look(
                    "⏳",
                    WARN,
                    messages.get(MessageKey.TRANSLATING_STUCK_TITLE),
                    line + "\n" + messages.get(MessageKey.TRANSLATING_STUCK_HINT),
                    Offer.STUCK);
        }
        return new Look(plain.glyph(), plain.role(), plain.title(), line, Offer.NONE);
    }

    private String waitingLine(final WaitingCall waiting) {
        final Duration timeout = waiting.timeout();
        final boolean retried = !waiting.totalWaited().equals(waiting.attemptWaited());
        final String locator = waiting.locator().isEmpty()
                ? ""
                : " · " + waiting.locator() + (waiting.moreSegments() > 0 ? " +" + waiting.moreSegments() : "");
        return messages.get(
                MessageKey.TRANSLATING_WAITING_CALL,
                callName(waiting.kind()),
                locator,
                String.valueOf(waiting.attempt()),
                String.valueOf(waiting.maxAttempts()),
                clock(waiting.attemptWaited()),
                timeout == null ? NONE : clock(timeout),
                retried ? clock(waiting.totalWaited()) : NONE);
    }

    private Look noticeLook(final RunState state, final RunNotice notice, final boolean offerProvider) {
        return switch (notice) {
            case RunNotice.ProviderError provider ->
                new Look(
                        "⛔",
                        ERR,
                        provider.error().title(),
                        providerText(provider),
                        state != RunState.PAUSED ? Offer.SETTINGS_ONLY : offerProvider ? Offer.PROVIDER : Offer.NONE);
            case RunNotice.Refused refused ->
                new Look(
                        "⚠",
                        WARN,
                        messages.get(MessageKey.TRANSLATING_REFUSED_TITLE),
                        refused.error().message(),
                        Offer.NONE);
            case RunNotice.MissingInput missing ->
                new Look(
                        "⚠",
                        WARN,
                        messages.get(MessageKey.TRANSLATING_MISSING_TITLE),
                        messages.get(
                                MessageKey.TRANSLATING_MISSING_TEXT,
                                missing.which().token()),
                        Offer.NONE);
        };
    }

    // The pause's details are worded only while the run is paused on this error; a failed preparation has none.
    private String providerText(final RunNotice.ProviderError provider) {
        final String server = provider.endpointHost()
                .map(host -> messages.get(MessageKey.TRANSLATING_PROVIDER_TEXT, host))
                .orElseGet(() -> messages.get(MessageKey.TRANSLATING_PROVIDER_TEXT_NO_HOST));
        final PauseNotice pause = mirror.review().pauseNotice().get();
        if (pause == null || mirror.runState().get() != RunState.PAUSED) {
            return server;
        }
        final CallKind kind = pause.kind();
        final String detail = messages.get(
                MessageKey.TRANSLATING_PROVIDER_DETAIL,
                messages.get(MessageKey.RUN_CALL_KIND, kind == null ? "other" : WaitingCall.tokenOf(kind)),
                pause.locator().isEmpty() ? "" : " · " + pause.locator(),
                String.valueOf(pause.code()),
                String.valueOf(pause.pauses()),
                String.valueOf(pause.pausesBeforeFlagging()));
        final String resume =
                messages.get(MessageKey.TRANSLATING_PROVIDER_RESUME, pause.isLastPause() ? "last" : "more");
        return server + "\n" + detail + "\n" + resume;
    }

    private Look stateLook(final RunState state) {
        return switch (state) {
            case IDLE -> plainLook("ℹ", INFO, "idle");
            case RUNNING -> plainLook("▶", INFO, "running");
            case PAUSING -> plainLook("⏸", INFO, "pausing");
            case PAUSED -> pausedLook();
            case STOPPING -> plainLook("⏹", INFO, "stopping");
            case STOPPED -> plainLook("⏹", INFO, "stopped");
            case COMPLETED -> plainLook("✓", OK, "completed");
            case FAILED -> plainLook("⚠", WARN, "failed");
        };
    }

    // The chunk the run continues at is named once it is known; before that the plain paused text stands.
    private Look pausedLook() {
        if (mirror.chunks().get() == 0) {
            return plainLook("⏸", INFO, "paused");
        }
        return new Look(
                "⏸",
                INFO,
                messages.get(MessageKey.TRANSLATING_STATE_TITLE, "paused"),
                messages.get(
                        MessageKey.TRANSLATING_PAUSED_TEXT,
                        mirror.chunk().get(),
                        mirror.chunks().get()),
                Offer.NONE);
    }

    private Look plainLook(final String glyph, final Banner.Role role, final String token) {
        return new Look(
                glyph,
                role,
                messages.get(MessageKey.TRANSLATING_STATE_TITLE, token),
                messages.get(MessageKey.TRANSLATING_STATE_TEXT, token),
                Offer.NONE);
    }

    private String callName(final CallKind kind) {
        return messages.get(MessageKey.RUN_CALL_KIND, WaitingCall.tokenOf(kind));
    }

    private static String clock(final Duration duration) {
        final long seconds = duration.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
    }
}
