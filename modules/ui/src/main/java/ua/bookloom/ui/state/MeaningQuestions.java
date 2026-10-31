package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Reads the names waiting for the once-per-name question about a meaning the book plays on, and stores the answers
 * (design D5). The glossary is read and written off the FX thread; the answer is delivered on it. A failed read is
 * "none" and a failed write is logged: the question is a courtesy and must never stop a run from starting, and an
 * unanswered name is transcribed by default.
 */
@Slf4j
@Singleton
public final class MeaningQuestions {

    private final GlossaryCalls calls;
    private final GlossaryService glossary;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param glossary the port the names are read and answered through
     * @param executor the daemon executor the calls run on, never the FX thread
     */
    @Inject
    public MeaningQuestions(final GlossaryService glossary, final @BackgroundExecutor ExecutorService executor) {
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.calls = new GlossaryCalls(executor);
    }

    /**
     * Reads the names to ask about.
     *
     * @param projectId the stored project
     * @param onFx receives the names on the FX thread; empty when none waits or on failure
     */
    public void find(final String projectId, final Consumer<List<GlossaryEntry>> onFx) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(onFx, "onFx");
        log.debug("looking for names of project {} whose meaning question waits", projectId);
        calls.run("meaning questions", () -> glossary.meaningQuestions(projectId), answer -> {
            final List<GlossaryEntry> found = answer.data() == null ? List.of() : answer.data();
            log.debug("project {} has {} names to ask about, ok {}", projectId, found.size(), answer.isOk());
            onFx.accept(found);
        });
    }

    /**
     * Stores the answers.
     *
     * @param projectId the stored project
     * @param answers the answer per entry id
     * @param onFx what runs on the FX thread once the answers are stored, or failed to be
     */
    public void answer(final String projectId, final Map<String, MeaningChoice> answers, final Runnable onFx) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(answers, "answers");
        Objects.requireNonNull(onFx, "onFx");
        log.debug("storing {} meaning answers for project {}", answers.size(), projectId);
        calls.run("meaning answers", () -> glossary.answerMeaning(projectId, answers), stored -> {
            if (stored.isErr()) {
                log.warn("the meaning answers of project {} were not stored; the names stay transcribed", projectId);
            }
            onFx.run();
        });
    }
}
