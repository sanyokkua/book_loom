package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.RunStateView.BannerView;
import ua.bookloom.ui.state.RunStateView.RunAction;
import ua.bookloom.ui.state.RunStateView.Text;

/**
 * The pinned banner of each run-state row (README "Run states", L3; notifications "Report a paused or failed run in
 * the run's own banner"). A pause on an error offers Try again now, Skip these N segments with the real count, and
 * Open settings; a missing model offers another model; a failed run offers its details and opens nothing by itself.
 * Pure; logs nothing.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunBanners {

    private static final String REACH = "reach";
    private static final String OTHER = "other";

    static @Nullable BannerView of(final RunRow row, final RunStateInputs in) {
        return switch (row) {
            case READY, TRANSLATING, PAUSING, PAUSED, STOPPING, STOPPED -> null;
            case READY_TO_CONTINUE -> welcome(in);
            case CANNOT_START -> cannotStart(in);
            case WAITING -> waiting(in);
            case PAUSED_AFTER_ERROR -> pausedAfterError(in);
            case STOPPED_TRYING -> gaveUp(in);
            case MODEL_MISSING -> modelMissing(in);
            case STUCK -> stuck(in);
            case FINISHED_TO_REVIEW -> finishedToReview(in);
            case FINISHED_NONE -> finishedNone();
            case FAILED -> failed();
        };
    }

    private static BannerView finishedNone() {
        return new BannerView(
                "finishedNone",
                Banner.Role.OK,
                "✓",
                Text.of(MessageKey.RUN_BANNER_FINISHED_NONE),
                null,
                List.of(),
                true);
    }

    private static BannerView failed() {
        return banner(
                "failed",
                Banner.Role.ERR,
                "⛔",
                Text.of(MessageKey.RUN_BANNER_FAILED),
                null,
                List.of(action(
                        RunStateView.SHOW_DETAILS,
                        MessageKey.RUN_ACTION_SHOW_DETAILS,
                        MessageKey.RUN_ACTION_SHOW_DETAILS_TIP)));
    }

    private static BannerView welcome(final RunStateInputs in) {
        return banner(
                "welcome",
                Banner.Role.INFO,
                "↺",
                Text.of(MessageKey.RUN_BANNER_WELCOME, in.decided(), in.total()),
                Text.of(MessageKey.RUN_BANNER_WELCOME_TEXT, RunStateView.orNone(in.continuesAt())),
                List.of());
    }

    private static BannerView waiting(final RunStateInputs in) {
        final String provider = in.model().provider();
        return banner(
                "waiting",
                Banner.Role.WARN,
                "⏳",
                Text.of(MessageKey.RUN_BANNER_WAITING, provider),
                Text.of(MessageKey.RUN_BANNER_WAITING_TEXT, provider, nextTry(in.recovery())),
                pausedOnErrorActions(in));
    }

    private static BannerView gaveUp(final RunStateInputs in) {
        final String provider = in.model().provider();
        return banner(
                "gaveUp",
                Banner.Role.ERR,
                "⛔",
                Text.of(MessageKey.RUN_BANNER_GAVE_UP, provider, hoursGivenUp()),
                Text.of(MessageKey.RUN_BANNER_GAVE_UP_TEXT, provider),
                pausedOnErrorActions(in));
    }

    private static BannerView modelMissing(final RunStateInputs in) {
        return banner(
                "modelMissing",
                Banner.Role.ERR,
                "⛔",
                Text.of(MessageKey.RUN_BANNER_MODEL_MISSING, in.model().modelId()),
                Text.of(MessageKey.RUN_BANNER_MODEL_MISSING_TEXT, in.model().provider()),
                List.of(action(
                        RunStateView.CHOOSE_MODEL,
                        MessageKey.RUN_ACTION_CHOOSE_MODEL,
                        MessageKey.RUN_ACTION_CHOOSE_MODEL_TIP)));
    }

    private static long hoursGivenUp() {
        return TranslationJob.DEFAULT_MAX_OUTAGE.toHours();
    }

    private static BannerView banner(
            final String key,
            final Banner.Role role,
            final String glyph,
            final Text title,
            final @Nullable Text text,
            final List<RunAction> actions) {
        return new BannerView(key, role, glyph, title, text, actions, false);
    }

    // The README's case is a missing AI model; a refusal or a failed preparation is told in its own words.
    private static BannerView cannotStart(final RunStateInputs in) {
        final RunAction settings = openSettings();
        return switch (in.notice()) {
            // A refusal can come after work was done (an export refused at the run's end), so it is not "cannot start".
            case RunNotice.Refused refused ->
                banner(
                        "refused",
                        Banner.Role.WARN,
                        "⚠",
                        Text.of(MessageKey.TRANSLATING_REFUSED_TITLE),
                        Text.of(MessageKey.RUN_BANNER_PLAIN, refused.error().message()),
                        List.of());
            case RunNotice.ProviderError provider ->
                cannotStartBanner(
                        OTHER,
                        Text.of(MessageKey.RUN_BANNER_PLAIN, provider.error().message()),
                        List.of(settings));
            case RunNotice.MissingInput missing -> {
                final String token = missing.which().token();
                yield cannotStartBanner(
                        token,
                        Text.of(MessageKey.TRANSLATING_MISSING_TEXT, token),
                        missing.which() == RunNotice.Input.MODEL ? List.of(settings) : List.of());
            }
            case null -> {
                final String token = RunNotice.Input.MODEL.token();
                yield cannotStartBanner(token, Text.of(MessageKey.TRANSLATING_MISSING_TEXT, token), List.of(settings));
            }
        };
    }

    private static BannerView cannotStartBanner(final String token, final Text text, final List<RunAction> actions) {
        return banner(
                "cannotStart",
                Banner.Role.WARN,
                "⚠",
                Text.of(MessageKey.RUN_BANNER_CANNOT_START, token),
                text,
                actions);
    }

    private static BannerView pausedAfterError(final RunStateInputs in) {
        final String reach = RunChips.isUnreachable(in.pausedOn()) ? REACH : OTHER;
        final String provider = in.model().provider();
        return banner(
                "pausedError",
                Banner.Role.ERR,
                "⏸",
                Text.of(MessageKey.RUN_BANNER_PAUSED_ERROR, provider, reach),
                Text.of(MessageKey.RUN_BANNER_PAUSED_ERROR_TEXT, provider, reach),
                pausedOnErrorActions(in));
    }

    // The one clock of a stuck call: the attempt's wait against its own limit; nothing for the person to do.
    private static BannerView stuck(final RunStateInputs in) {
        final WaitingCall waiting = in.waiting();
        final @Nullable Duration limit = waiting == null ? null : waiting.timeout();
        return banner(
                "stuck",
                Banner.Role.WARN,
                "⏳",
                Text.of(
                        MessageKey.RUN_BANNER_STUCK,
                        RunChips.stuckClock(in),
                        limit == null ? RunStateView.NONE : RunStateView.clock(limit)),
                null,
                List.of());
    }

    private static BannerView finishedToReview(final RunStateInputs in) {
        final RunAction review = new RunAction(
                RunStateView.REVIEW_SEGMENTS,
                Text.of(MessageKey.RUN_ACTION_REVIEW_SEGMENTS, in.toReview()),
                false,
                true,
                Text.of(MessageKey.RUN_ACTION_REVIEW_SEGMENTS_TIP));
        return new BannerView(
                "finishedReview",
                Banner.Role.OK,
                "✓",
                Text.of(MessageKey.RUN_BANNER_FINISHED_REVIEW, in.toReview()),
                null,
                List.of(review),
                true);
    }

    private static List<RunAction> pausedOnErrorActions(final RunStateInputs in) {
        final RunAction retry = new RunAction(
                RunStateView.RETRY,
                Text.of(MessageKey.RUN_ACTION_TRY_AGAIN),
                false,
                in.controls().resume().isEnabled(),
                Text.of(MessageKey.RUN_ACTION_TRY_AGAIN_TIP));
        final RunAction skip = new RunAction(
                RunStateView.SKIP,
                Text.of(MessageKey.RUN_ACTION_SKIP, Math.max(1, in.inFlight())),
                false,
                true,
                Text.of(MessageKey.RUN_ACTION_SKIP_TIP));
        return List.of(retry, skip, openSettings());
    }

    private static RunAction openSettings() {
        return action(
                RunStateView.OPEN_SETTINGS,
                MessageKey.TRANSLATING_OPEN_SETTINGS,
                MessageKey.TRANSLATING_OPEN_SETTINGS_TIP);
    }

    private static RunAction action(final String id, final MessageKey label, final MessageKey tip) {
        return new RunAction(id, Text.of(label), false, true, Text.of(tip));
    }

    private static String nextTry(final @Nullable RecoveryState recovery) {
        return RunStateView.clock(Duration.ofSeconds(recovery == null ? 0 : recovery.secondsLeft()));
    }
}
