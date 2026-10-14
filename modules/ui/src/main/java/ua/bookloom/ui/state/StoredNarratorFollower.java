package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Brings a narrator the run inferred into the window's brief when the run ends. The run stores the narrator it found
 * in the project, which the window never reads again; a brief edit saves the whole brief, so without this the first
 * edit after a run would write the old narrator over the inferred one. Only an INFERRED narrator is taken (it has no
 * quotes to recheck, so the assistant answers with the stored one), and a narrator the person set in the window is
 * never replaced.
 *
 * <p>A singleton created with the injector, because a run can end while no brief screen exists.
 */
@Slf4j
@Singleton
public final class StoredNarratorFollower {

    private final CurrentProject current;
    private final SetupAssistant assistant;
    private final ExecutorService executor;

    /**
     * Starts following the run's end.
     *
     * @param mirror the run's state, whose ending triggers the look at the stored narrator
     * @param current the open book and its brief, which takes the stored narrator
     * @param assistant the port that answers the stored narrator without a model call
     * @param executor the daemon executor the stored project is read on, never the FX thread
     */
    @Inject
    public StoredNarratorFollower(
            final StateMirror mirror,
            final CurrentProject current,
            final SetupAssistant assistant,
            @BackgroundExecutor final ExecutorService executor) {
        this.current = Objects.requireNonNull(current, "current");
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.executor = Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(mirror, "mirror").runState().addListener((observed, was, now) -> onRunState(now));
    }

    private void onRunState(final RunState state) {
        if (!state.hasEnded()) {
            return;
        }
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("the run ended as {} with no book open: no narrator to follow", state);
            return;
        }
        final String projectId = book.projectId();
        log.debug("the run ended as {}: reading the stored narrator of project {}", state, projectId);
        try {
            executor.execute(() -> read(projectId));
        } catch (RejectedExecutionException rejected) {
            log.warn("the stored narrator of project {} could not be read", projectId, rejected);
        }
    }

    private void read(final String projectId) {
        try {
            final Result<Narrator> stored = assistant.recheckNarrator(projectId);
            Platform.runLater(() -> adopt(projectId, stored));
        } catch (RuntimeException thrown) {
            log.error(
                    "reading the stored narrator of project {} threw instead of returning a result", projectId, thrown);
        }
    }

    private void adopt(final String projectId, final Result<Narrator> stored) {
        final Narrator narrator = stored.data();
        final OpenedBook book = current.book().get();
        final BookBrief brief = current.brief().get();
        if (narrator == null
                || book == null
                || brief == null
                || !book.projectId().equals(projectId)) {
            log.debug("the stored narrator of project {} is not taken: unread or another book is open", projectId);
            return;
        }
        if (narrator.genderOrigin() != GenderOrigin.INFERRED || isKept(brief.narrator(), narrator)) {
            log.debug("the stored narrator of project {} changes nothing in the window", projectId);
            return;
        }
        log.info("the window takes the narrator the run inferred for project {}: {}", projectId, narrator.gender());
        current.replaceBrief(brief.withNarrator(narrator));
    }

    private static boolean isKept(final @Nullable Narrator shown, final Narrator stored) {
        return stored.equals(shown) || (shown != null && shown.genderOrigin() == GenderOrigin.PERSON);
    }
}
