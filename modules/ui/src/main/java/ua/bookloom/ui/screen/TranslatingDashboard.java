package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/**
 * The built translating screen, and the one thing about it that changes with events rather than with a bound
 * property: the banner that says where the run is.
 *
 * <p>The banner is one node whose words and role class are replaced, not one node per state, so that a state change
 * moves nothing on screen. Its role is never the only carrier of meaning: each state also has its own glyph and title.
 * A notice, when there is one, is worded in the same banner in place of the plain state; a provider failure offers the
 * route to the provider settings, and a paused run also Retry now, Skip segment and Stay paused; a run that waits for
 * the provider by itself offers Retry now, Skip segment and Stop. A request that has waited too long offers Skip
 * segment, Retry now and Pause. {@link BannerLooks} chooses the words.
 */
@Slf4j
final class TranslatingDashboard {

    /** The banner, which {@link #render(RunState, RunNotice, int)} rewrites, and every action it may offer. */
    record LiveBanner(
            Banner banner,
            Button retry,
            Button skip,
            Button settings,
            Button stay,
            Button sendAgain,
            Button pauseNow,
            Button stopRun) {}

    private final Node root;
    private final LiveBanner banner;
    private final StateMirror mirror;
    private final BannerLooks looks;
    private RunState shownState = RunState.IDLE;
    private @Nullable RunNotice shownNotice;
    private int shownWait = StateMirror.NOT_WAITING;
    private BannerLooks.@Nullable Offer shownOffer;
    private @Nullable AppError stayedPausedOn;

    TranslatingDashboard(final Node root, final LiveBanner banner, final Messages messages, final StateMirror mirror) {
        this.root = Objects.requireNonNull(root, "root");
        this.banner = Objects.requireNonNull(banner, "banner");
        banner.stay().setOnAction(event -> stayPaused());
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.looks = new BannerLooks(Objects.requireNonNull(messages, "messages"), mirror);
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
        shownState = state;
        shownNotice = notice;
        shownWait = waitingSeconds;
        final BannerLooks.Look look = looks.lookOf(
                state, notice, waitingSeconds, mirror.live().waitingCall().get(), !isStayedPausedOn(notice));
        banner.banner().setGlyph(look.glyph());
        banner.banner().setTitle(look.title());
        banner.banner().setText(look.text());
        banner.banner().setRole(look.role());
        offer(look.offer());
    }

    private void offer(final BannerLooks.Offer offer) {
        if (offer != shownOffer) {
            log.debug("banner offers {}", offer);
            shownOffer = offer;
        }
        final boolean provider = offer == BannerLooks.Offer.PROVIDER;
        final boolean stuck = offer == BannerLooks.Offer.STUCK;
        final boolean recovering = offer == BannerLooks.Offer.RECOVERING;
        Banner.setActionShown(banner.retry(), provider || recovering);
        Banner.setActionShown(banner.skip(), provider || stuck || recovering);
        Banner.setActionShown(banner.stopRun(), recovering);
        Banner.setActionShown(banner.settings(), provider || offer == BannerLooks.Offer.SETTINGS_ONLY);
        Banner.setActionShown(banner.stay(), provider);
        Banner.setActionShown(banner.sendAgain(), stuck);
        Banner.setActionShown(banner.pauseNow(), stuck);
    }

    // The withdrawal belongs to the error it was pressed on: the next error published is a new value and offers again.
    private void stayPaused() {
        if (shownNotice instanceof RunNotice.ProviderError provider) {
            log.debug(
                    "stay paused pressed: withdrawing the provider-error actions for {}",
                    provider.error().code());
            stayedPausedOn = provider.error();
            render(shownState, shownNotice, shownWait);
        }
    }

    // Identity, not equality: a later pause with an equal error is still a new pause and offers the actions again.
    @SuppressWarnings("ReferenceEquality")
    private boolean isStayedPausedOn(final @Nullable RunNotice notice) {
        return notice instanceof RunNotice.ProviderError provider && provider.error() == stayedPausedOn;
    }
}
