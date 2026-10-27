package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.settings.AppSetting;

/**
 * Decides when the review desk opens by itself: a run that was translating has just completed, the person's
 * {@code automation.openReviewDesk} is on, and segments are left to review. A project reopened in its completed state
 * never opens it, nor does a stopped or failed run. The setting and the count are read off the FX thread; the answer
 * is given on it.
 */
@Slf4j
public final class ReviewDeskTrigger {

    private static final Set<RunState> TRANSLATING = Set.of(RunState.RUNNING, RunState.PAUSING, RunState.STOPPING);

    private final Supplier<ReviewDesk> desk;
    private final CurrentProject current;
    private final SettingsRepository settings;
    private final ExecutorService executor;
    private final IntConsumer open;

    /**
     * Starts following the run's state.
     *
     * @param mirror the run's state
     * @param current the open book, whose project is counted
     * @param desk the review desk the count is read from, reached only when a run completes
     * @param settings where the person's choice is stored
     * @param executor where the reads run, never the FX thread
     * @param open told on the FX thread, with the count, when the desk should open
     */
    public ReviewDeskTrigger(
            final StateMirror mirror,
            final CurrentProject current,
            final Supplier<ReviewDesk> desk,
            final SettingsRepository settings,
            final ExecutorService executor,
            final IntConsumer open) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.current = Objects.requireNonNull(current, "current");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.open = Objects.requireNonNull(open, "open");
        mirror.runState().addListener((observed, was, now) -> changed(was, now));
    }

    private void changed(final @Nullable RunState was, final RunState now) {
        if (now != RunState.COMPLETED) {
            return;
        }
        final OpenedBook book = current.book().get();
        log.debug("run state {} -> {} with book {}", was, now, book == null ? null : book.projectId());
        if (was == null || !TRANSLATING.contains(was) || book == null) {
            log.debug("the review desk does not open by itself: no translating run has just completed");
            return;
        }
        final String projectId = book.projectId();
        try {
            executor.execute(() -> decide(projectId));
        } catch (RejectedExecutionException closing) {
            log.debug("the review desk does not open by itself: the executor is shut down", closing);
        }
    }

    private void decide(final String projectId) {
        final boolean on = isOn();
        final int flagged = new ReviewQueries(desk.get()).flagged(projectId, 0);
        log.debug("run completed on project {}: open the desk {}, {} to review", projectId, on, flagged);
        if (!on || flagged <= 0) {
            log.debug("the review desk does not open by itself: setting {}, {} to review", on, flagged);
            return;
        }
        Platform.runLater(() -> open.accept(flagged));
    }

    private boolean isOn() {
        final var read = settings.get(AppSetting.OPEN_REVIEW_DESK).data();
        final boolean fallback = Boolean.TRUE.equals(AppSetting.OPEN_REVIEW_DESK.defaultValue());
        if (read == null) {
            log.warn("the review desk setting could not be read: using its default {}", fallback);
            return fallback;
        }
        return read.orElse(fallback);
    }
}
