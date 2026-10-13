package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryModelScans;
import ua.bookloom.pipeline.glossary.GlossaryServiceImpl;
import ua.bookloom.pipeline.glossary.LexiconReading;
import ua.bookloom.pipeline.glossary.PreScan;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.glossary.TermChoice;
import ua.bookloom.pipeline.glossary.TermReview;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.lexicon.LexiconServiceImpl;
import ua.bookloom.pipeline.prompt.PromptTemplates;
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

        /** The project's book index, as the glossary and lexicon services read it. */
        public BookIndex index() {
            return Objects.requireNonNull(project.stores().openProjects().index(project.id()), "book index");
        }

        /** The body segments the scans read, in reading order. */
        public List<Segment> story() {
            final List<Segment> all = document().units().stream()
                    .flatMap(unit -> unit.segments().stream())
                    .toList();
            return index().story().stream().map(all::get).toList();
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
     * Names &amp; style as the app wires it, over the prepared book's own stores.
     *
     * @param glossary the production glossary service
     * @param lexicon the production lexicon service
     */
    public record Names(GlossaryService glossary, LexiconService lexicon) {}

    /** The glossary and lexicon services over the prepared book's stores (the names eval reaches them from here). */
    public static Names names(final Prepared prepared) {
        final PromptTemplates templates = new PromptTemplates();
        final ObjectMapper mapper = new ObjectMapper();
        final RunStores stores = prepared.stores();
        final SuggestTargets suggest = new SuggestTargets(templates, mapper);
        final TermReview review = new TermReview(templates, mapper, stores.glossary(), suggest);
        final GlossaryModelScans scans = new GlossaryModelScans(
                stores.projects(),
                stores.openProjects(),
                new PreScan(templates, mapper, stores.glossary(), review, suggest),
                review,
                suggest,
                new LexiconReading(templates, mapper, new TermChoice(templates, mapper)),
                Clock.systemUTC());
        return new Names(
                new GlossaryServiceImpl(
                        stores.glossary(),
                        stores.segments(),
                        prepared.project.deferrals(),
                        stores.projects(),
                        stores.openProjects(),
                        scans),
                new LexiconServiceImpl(
                        stores.lexicon(), stores.glossary(), stores.projects(), stores.openProjects(), scans));
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
