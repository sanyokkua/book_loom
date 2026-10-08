package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.review.RetryDraft;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.run.RunStores;

/**
 * The package-private job wiring of the whole-book tests ({@link TranslationJobTestSupport}, {@link BatchedJobs}) made
 * reachable from the eval package, so the sequence eval runs the same job the tests do instead of a second copy of it.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SequenceJobs {

    /** A book imported through the real project service into the in-memory stores, ready to run. */
    public static final class Prepared {

        private final TestProject project;

        private Prepared(final TestProject project) {
            this.project = project;
        }

        /** The stored project's id. */
        public String projectId() {
            return project.id();
        }

        /** The stores the run reads and writes. */
        public RunStores stores() {
            return project.stores();
        }

        /** The parsed book the run translates. */
        public Document document() {
            return Objects.requireNonNull(project.stores().openProjects().get(project.id()), "open document");
        }
    }

    /** A brief on the given dial and narrator, in the job tests' defaults otherwise. */
    public static BookBrief brief(
            final String source, final String target, final QualityDial dial, final Narrator narrator) {
        return TranslationJobTestSupport.brief(source, target, dial).withNarrator(narrator);
    }

    /** Imports {@code book} under {@code brief}. */
    public static Prepared importBook(final Path book, final BookBrief brief) {
        return new Prepared(TranslationJobTestSupport.project(book, brief));
    }

    /** The consistency pass over the prepared book's own stores (the eval's stage runners reach it from here). */
    public static ConsistencyPass consistencyPass(final Prepared prepared) {
        return ConsistencyPassFixture.over(prepared.project);
    }

    /** The review desk's retry over the prepared book's own stores. */
    public static RetryDraft retryDraft(final Prepared prepared) {
        return ConsistencyPassFixture.retryDraft(prepared.project);
    }

    /**
     * Runs the batched job over the prepared book to its end.
     *
     * @param window the context window the provider reported, or null for the run's default
     */
    public static Result<JobReport> run(
            final Prepared prepared,
            final ChatModel model,
            final ReviewMode mode,
            @Nullable final Integer window,
            final JobListener listener) {
        final TranslationJobImpl job =
                BatchedJobs.batchedJob(prepared.project, model, new RunRequest(prepared.project.id(), mode, window));
        job.subscribe(listener);
        return job.run();
    }
}
