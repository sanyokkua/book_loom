package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ReviewDeskTrigger;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TechnicalDetails;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * Opens the review desk: when the person asks (Review (n), the strip's "n to review", a finished run's banner), when a
 * review pause names a segment, and by itself when a translating run completes with segments to review and
 * {@code automation.openReviewDesk} is on.
 *
 * <p>An eager singleton, so the desk opens by itself whichever screen is shown; everything the desk needs beyond the
 * run's state is reached through a provider when it first opens, so building the window never builds the desk. The card is built once, lazily, on the
 * FX thread, and lives as long as the application, like the view model it shows. A desk nobody asked for waits for the
 * window to be free ({@link ModalHost#showWhenFree}); at most one such opening waits at a time.
 */
@Slf4j
@Singleton
public final class ReviewDeskLauncher {

    private final Provider<ReviewViewModel> viewModels;
    private final ModalHost modals;
    private final Messages messages;
    private final CurrentProject current;
    private final LanguageNames names;
    private final Provider<ReviewPauseFollower> pauses;
    private final Provider<TranslatingViewModel> translating;
    private final StateMirror mirror;
    private final Provider<TechnicalDetails> technical;
    private @Nullable ReviewDeskCard card;
    private boolean waiting;

    /**
     * Starts following review pauses and finished runs.
     *
     * @param viewModels the desk's view model
     * @param modals where the desk is shown
     * @param messages the catalogue the desk is worded from
     * @param current the open book, whose languages head the panes
     * @param names the names of those languages
     * @param pauses the review pause, which opens the desk on its segment
     * @param translating what the desk's Pause pauses
     * @param mirror the run's state, whose completion may open the desk
     * @param technical the shared technical-details switch
     * @param ports the review desk port, the settings store and the executor the opening decision reads with
     */
    @Inject
    public ReviewDeskLauncher(
            final Provider<ReviewViewModel> viewModels,
            final ModalHost modals,
            final Messages messages,
            final CurrentProject current,
            final LanguageNames names,
            final Provider<ReviewPauseFollower> pauses,
            final Provider<TranslatingViewModel> translating,
            final StateMirror mirror,
            final Provider<TechnicalDetails> technical,
            final Ports ports) {
        this.viewModels = Objects.requireNonNull(viewModels, "viewModels");
        this.modals = Objects.requireNonNull(modals, "modals");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.current = Objects.requireNonNull(current, "current");
        this.names = Objects.requireNonNull(names, "names");
        this.pauses = Objects.requireNonNull(pauses, "pauses");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.technical = Objects.requireNonNull(technical, "technical");
        Objects.requireNonNull(ports, "ports");
        new ReviewDeskTrigger(
                mirror, current, ports.desk()::get, ports.settings(), ports.executor(), this::openByItself);
        mirror.review().reviewPauseSegment().addListener((observed, was, now) -> {
            if (now != null) {
                openForPause(now);
            }
        });
    }

    /**
     * The ports the opening decision reads, injected as one so the launcher's constructor stays short.
     *
     * @param desk the review desk port, reached only when a run completes, so building the window never needs it
     * @param settings where {@code automation.openReviewDesk} is stored
     * @param executor where the reads run
     */
    public record Ports(
            Provider<ReviewDesk> desk,
            SettingsRepository settings,
            @BackgroundExecutor ExecutorService executor) {

        /** Receives the ports from the injector. */
        @Inject
        public Ports {
            Objects.requireNonNull(desk, "desk");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(executor, "executor");
        }
    }

    /**
     * Opens the desk at once on a filter's first row, replacing any card shown. FX thread only.
     *
     * @param filter the list to show
     */
    public void open(final ReviewFilter filter) {
        Objects.requireNonNull(filter, "filter");
        log.info("review desk opened (asked) filter={}", filter);
        viewModels.get().openOn(filter, null);
        modals.show(card(), false, true);
    }

    private void openByItself(final int toReview) {
        log.info("review desk opens by itself: {} segments to review", toReview);
        viewModels.get().openOn(ReviewFilter.ALL_FLAGGED, null);
        whenFree();
    }

    private void openForPause(final String segmentId) {
        log.info("review desk opened (for a pause) segment={}", segmentId);
        viewModels.get().openOn(ReviewFilter.ALL_FLAGGED, segmentId);
        whenFree();
    }

    // At most one opening waits: a second one while the first waits would show the desk twice.
    private void whenFree() {
        final ReviewDeskCard desk = card();
        if (desk.getScene() != null) {
            log.debug("the review desk is shown already");
        } else if (waiting) {
            log.debug("the review desk already waits for the window to be free");
        } else {
            waiting = true;
            modals.showWhenFree(desk);
        }
    }

    private ReviewDeskCard card() {
        final ReviewDeskCard built = card;
        if (built != null) {
            return built;
        }
        log.debug("building the review desk");
        final ReviewDeskCard.Parts parts = new ReviewDeskCard.Parts(
                TranslatingView.languageName(
                        current, BookBrief::sourceLanguage, MessageKey.LIVE_SOURCE_FALLBACK, names, messages),
                TranslatingView.languageName(
                        current, BookBrief::targetLanguage, MessageKey.LIVE_TARGET_FALLBACK, names, messages),
                pauses.get().acceptContinues(),
                mirror.live().suspicious(),
                technical.get().property(),
                () -> translating.get().pause());
        final ReviewDeskCard desk = new ReviewDeskCard(viewModels.get(), parts, messages, this::close);
        desk.sceneProperty().addListener((observed, was, now) -> {
            if (now != null) {
                waiting = false;
            }
        });
        card = desk;
        return desk;
    }

    private void close() {
        log.info("review desk closed");
        modals.hide();
    }
}
