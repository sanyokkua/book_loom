package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Judges the open book's narration before the Start question when the import could not: a book that declares no
 * language (Markdown, plain text) is judged only once the person has chosen its source, so its hint is still unknown
 * when Start is pressed. The detection reads the whole book with no model call, on the background pool. FX thread only.
 */
@Slf4j
@Singleton
public final class NarratorDetection {

    private final CurrentProject current;
    private final SetupAssistant assistant;
    private final ExecutorService executor;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param current the open book, whose hint is filled in
     * @param assistant the port that judges the book's text with the source language of its stored brief
     * @param executor the background pool the detection runs on, because it reads the whole book
     */
    @Inject
    public NarratorDetection(
            final CurrentProject current,
            final SetupAssistant assistant,
            @BackgroundExecutor final ExecutorService executor) {
        this.current = Objects.requireNonNull(current, "current");
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Runs {@code then} once the open book's hint is known: at once when it is, else after the detection, whether it
     * found anything or failed (a failure only means nothing is asked). Nothing runs when another book is open by
     * then.
     *
     * @param projectId the open project
     * @param then what follows, on the FX thread
     */
    public void whenKnown(final String projectId, final Runnable then) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(then, "then");
        if (current.narratorHint().get() != null) {
            log.debug("the narrator hint of project {} is known: no detection", projectId);
            then.run();
            return;
        }
        log.debug("the narrator hint of project {} is unknown: detecting it before the start question", projectId);
        try {
            executor.execute(() -> {
                final @Nullable NarratorHint hint = detect(projectId);
                Platform.runLater(() -> detected(projectId, hint, then));
            });
        } catch (RuntimeException rejected) {
            log.warn("the narrator detection of project {} could not be submitted", projectId, rejected);
            then.run();
        }
    }

    private @Nullable NarratorHint detect(final String projectId) {
        try {
            final Result<NarratorHint> answer = assistant.detectNarrator(projectId);
            if (answer.isErr()) {
                log.warn(
                        "the narrator of project {} could not be detected: {}",
                        projectId,
                        Objects.requireNonNull(answer.error(), "error").code());
            }
            return answer.data();
        } catch (RuntimeException thrown) {
            log.error("the narrator detection of project {} threw instead of returning a result", projectId, thrown);
            return null;
        }
    }

    private void detected(final String projectId, final @Nullable NarratorHint hint, final Runnable then) {
        final OpenedBook book = current.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("the book changed while the narrator of project {} was detected: dropped", projectId);
            return;
        }
        if (hint != null) {
            log.info("narrator of project {} detected at start: {}", projectId, hint.person());
            current.replaceNarratorHint(hint);
        }
        then.run();
    }
}
