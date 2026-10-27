package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Decides at Start translation whether the narrator question must be asked: once per book in a session, when the
 * source was found to be told in the first person and the brief does not say the narrator's gender, and only after the
 * narrator's stored quotes were re-checked against the glossary. Writes the answer to the brief and starts only once it
 * is stored. FX thread only, apart from the re-check, which runs on the background pool.
 */
@Slf4j
@Singleton
public final class NarratorQuestion {

    private final CurrentProject project;
    private final BookBriefViewModel brief;
    private final SetupAssistant assistant;
    private final ExecutorService executor;
    private final NarratorDetection detection;
    // Touched on the FX thread only; holds the books the question has been answered for.
    private final Set<String> answered = new HashSet<>();
    // The books whose narrator is being re-checked in the background; a second Start meanwhile is ignored.
    private final Set<String> rechecking = new HashSet<>();

    /**
     * Creates the decision.
     *
     * @param project the holder of the open book, whose narrator hint and brief decide whether to ask
     * @param brief the view model the chosen or verified narrator is written through
     * @param assistant the port that re-checks the narrator's stored quotes before the question is asked
     * @param executor the background pool the re-check runs on, because it reads the whole book
     * @param detection what judges a book the import could not, before the question is decided
     */
    @Inject
    public NarratorQuestion(
            final CurrentProject project,
            final BookBriefViewModel brief,
            final SetupAssistant assistant,
            @BackgroundExecutor final ExecutorService executor,
            final NarratorDetection detection) {
        this.project = Objects.requireNonNull(project, "project");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.detection = Objects.requireNonNull(detection, "detection");
    }

    /**
     * Runs {@code start} at once when nothing must be asked or the re-check settled the narrator, else hands the
     * project to {@code ask}; either follows a moment later on the FX thread.
     *
     * @param start what starts the translation
     * @param ask what shows the question for the project with this id
     */
    public void startOrAsk(final Runnable start, final Consumer<String> ask) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(ask, "ask");
        final OpenedBook book = project.book().get();
        if (book == null) {
            log.debug("no book is open: the narrator is not asked about");
            start.run();
            return;
        }
        detection.whenKnown(book.projectId(), () -> askKnown(start, ask));
    }

    /**
     * Remembers the answer for the book, writes a gender to the brief, and starts once the brief is stored.
     *
     * @param projectId the project the question was asked about
     * @param gender the answer; {@link Gender#UNKNOWN} for "not stated", which writes nothing
     * @param start what starts the translation
     */
    public void answer(final String projectId, final Gender gender, final Runnable start) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(start, "start");
        log.debug("the narrator answered {} for project {}", gender, projectId);
        answered.add(projectId);
        if (gender != Gender.UNKNOWN) {
            brief.setFirstPersonNarrator(gender);
        }
        startWhenSaved(start);
    }

    private void askKnown(final Runnable start, final Consumer<String> ask) {
        final OpenedBook book = project.book().get();
        if (book == null || !mustAsk(book, project.brief().get())) {
            log.debug("the narrator is not asked about: starting");
            start.run();
            return;
        }
        final String projectId = book.projectId();
        if (!rechecking.add(projectId)) {
            log.debug("the narrator of project {} is already being re-checked: Start ignored", projectId);
            return;
        }
        log.debug("re-checking the narrator of project {} in the background", projectId);
        try {
            executor.execute(() -> {
                final Narrator verified = recheck(projectId);
                Platform.runLater(() -> rechecked(projectId, verified, start, ask));
            });
        } catch (RuntimeException rejected) {
            log.warn("the narrator re-check of project {} could not be submitted", projectId, rejected);
            rechecked(projectId, null, start, ask);
        }
    }

    // The glossary may have learned the name the brief's quote calls the narrator by since the brief was suggested; the
    // check calls no model, and a failed one only means the question is asked (15i.T5). It splits every paragraph of
    // the book, so it runs on the background pool.
    private @Nullable Narrator recheck(final String projectId) {
        try {
            final Result<Narrator> checked = assistant.recheckNarrator(projectId);
            final Narrator narrator = checked.data();
            if (checked.isErr() || narrator == null || narrator.gender() == Gender.UNKNOWN) {
                log.debug(
                        "the narrator re-check verified nothing for project {} (failed {})",
                        projectId,
                        checked.isErr());
                return null;
            }
            return narrator;
        } catch (RuntimeException thrown) {
            log.error("the narrator re-check of project {} threw instead of returning a result", projectId, thrown);
            return null;
        }
    }

    // Back on the FX thread: the open book may have changed while the check ran, in which case the question is
    // asked about the book that is open now, or not at all.
    private void rechecked(
            final String projectId,
            final @Nullable Narrator verified,
            final Runnable start,
            final Consumer<String> ask) {
        rechecking.remove(projectId);
        final OpenedBook book = project.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("the book changed while the narrator of project {} was re-checked: dropped", projectId);
            return;
        }
        if (!mustAsk(book, project.brief().get())) {
            log.debug("the narrator of project {} was settled while it was re-checked: starting", projectId);
            start.run();
            return;
        }
        if (verified != null) {
            log.info("the narrator re-check verified {} for project {}", verified.gender(), projectId);
            brief.styleSuggestion().adoptNarrator(verified);
            startWhenSaved(start);
            return;
        }
        log.debug("asking who narrates before starting project {}", projectId);
        ask.accept(projectId);
    }

    private boolean mustAsk(final OpenedBook book, final @Nullable BookBrief current) {
        return NarratorHints.needsGender(project.narratorHint().get(), current) && !answered.contains(book.projectId());
    }

    // The run reads the stored brief, and the save is asynchronous: starting before it lands would draft with the old
    // narrator while the window shows the new one.
    private void startWhenSaved(final Runnable start) {
        final ReadOnlyBooleanProperty saving = brief.saving();
        if (!saving.get()) {
            start.run();
            return;
        }
        log.debug("the narrator is being saved; the run starts when the brief is stored");
        saving.addListener(new ChangeListener<>() {
            @Override
            public void changed(
                    final ObservableValue<? extends Boolean> property, final Boolean was, final Boolean now) {
                if (!now) {
                    property.removeListener(this);
                    start.run();
                }
            }
        });
    }
}
