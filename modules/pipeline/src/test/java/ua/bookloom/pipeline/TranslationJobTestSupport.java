package ua.bookloom.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.ProjectServiceImpl;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.RunStores;

/** Shared real-document and controlled-model setup for translation job acceptance tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslationJobTestSupport {

    private static final long WAIT_SECONDS = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SentenceSplitter SPLITTER =
            Guice.createInjector(new DocumentModule()).getInstance(SentenceSplitter.class);
    private static final ConcurrentLinkedQueue<ExecutorService> EXECUTORS = new ConcurrentLinkedQueue<>();

    /** A book imported through a real {@code ProjectServiceImpl} over the in-memory repositories. */
    record TestProject(String id, RunStores stores, DocumentPort documents) {}

    static TestProject project(final Path book, final BookBrief brief) {
        return project(UnaryOperator.identity(), book, brief);
    }

    /**
     * Imports through the injector's own document port, so the inspector and the run share one registry, and hands
     * the job that port wrapped by {@code decorate}.
     */
    static TestProject project(final UnaryOperator<DocumentPort> decorate, final Path book, final BookBrief brief) {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        final DocumentPort documents = decorate.apply(injector.getInstance(DocumentPort.class));
        final RunStores stores = new RunStores(
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(CheckpointPort.class),
                injector.getInstance(OpenProjects.class),
                injector.getInstance(RunRepository.class),
                injector.getInstance(GlossaryRepository.class),
                injector.getInstance(TmRepository.class));
        final ProjectServiceImpl service = new ProjectServiceImpl(
                injector.getInstance(BookInspector.class),
                documents,
                stores.projects(),
                stores.segments(),
                stores.openProjects());
        final Result<ImportedBook> importResult = service.importBook(book);
        final ImportedBook imported =
                Objects.requireNonNull(importResult.data(), () -> "imported book: " + importResult.error());
        final String id = Objects.requireNonNull(imported.projectId(), "project id");
        service.updateBrief(id, brief);
        return new TestProject(id, stores, documents);
    }

    /** The brief the job tests run with: the Fast dial and the given languages, everything else at its default. */
    static BookBrief brief(@Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        final BookBrief defaults = BookBrief.defaults(sourceLanguage);
        return new BookBrief(
                sourceLanguage,
                targetLanguage,
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                defaults.names(),
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                defaults.alsoTranslate(),
                QualityDial.FAST);
    }

    /** The job-test brief on another dial. */
    static BookBrief brief(
            @Nullable final String sourceLanguage, @Nullable final String targetLanguage, final QualityDial dial) {
        final BookBrief fast = brief(sourceLanguage, targetLanguage);
        return new BookBrief(
                fast.sourceLanguage(),
                fast.targetLanguage(),
                fast.genre(),
                fast.register(),
                fast.voiceEra(),
                fast.audience(),
                fast.names(),
                fast.foreignPassages(),
                fast.footnotes(),
                fast.units(),
                fast.balance(),
                fast.alsoTranslate(),
                dial);
    }

    /**
     * The job-test brief with the book's metadata and navigation labels left untranslated, so a test that scripts one
     * reply per body segment of an EPUB never has an auxiliary segment ask for one.
     */
    static BookBrief epubBrief() {
        final BookBrief fast = brief("en", "uk");
        return new BookBrief(
                fast.sourceLanguage(),
                fast.targetLanguage(),
                fast.genre(),
                fast.register(),
                fast.voiceEra(),
                fast.audience(),
                fast.names(),
                fast.foreignPassages(),
                fast.footnotes(),
                fast.units(),
                fast.balance(),
                new AlsoTranslate(
                        false,
                        fast.alsoTranslate().altText(),
                        false,
                        fast.alsoTranslate().frontmatter()),
                fast.dial());
    }

    /** The job-test brief with the given "Also translate" switches. */
    static BookBrief briefWith(final AlsoTranslate alsoTranslate) {
        final BookBrief fast = brief("en", "uk");
        return new BookBrief(
                fast.sourceLanguage(),
                fast.targetLanguage(),
                fast.genre(),
                fast.register(),
                fast.voiceEra(),
                fast.audience(),
                fast.names(),
                fast.foreignPassages(),
                fast.footnotes(),
                fast.units(),
                fast.balance(),
                alsoTranslate,
                fast.dial());
    }

    static BookBrief withTarget(final BookBrief brief, final String targetLanguage) {
        return new BookBrief(
                brief.sourceLanguage(),
                targetLanguage,
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial());
    }

    static TranslationJobImpl job(final TestProject project, final ChatModel model) {
        return job(project, project.id(), model);
    }

    /** A job asked to run a project id that may not be the imported one. */
    static TranslationJobImpl job(final TestProject project, final String projectId, final ChatModel model) {
        return job(project, projectId, model, ReviewMode.UNATTENDED);
    }

    static TranslationJobImpl job(final TestProject project, final ChatModel model, final ReviewMode mode) {
        return job(project, project.id(), model, mode);
    }

    private static TranslationJobImpl job(
            final TestProject project, final String projectId, final ChatModel model, final ReviewMode mode) {
        return new TranslationJobImpl(
                project.documents(),
                new RunRequest(projectId, mode),
                model,
                new ObjectMapper(),
                new PromptTemplates(),
                project.stores(),
                qualityLoop(),
                SPLITTER,
                Clock.systemUTC());
    }

    /** The real quality loop over the real judge and self-heal calls, as Guice builds it for a run. */
    static QualityLoop qualityLoop() {
        return Guice.createInjector().getInstance(QualityLoop.class);
    }

    static TranslationJobImpl job(final Path source, final ChatModel model) {
        return job(project(source, brief("en", "uk")), model);
    }

    static TranslationJobImpl job(
            final UnaryOperator<DocumentPort> decorate, final Path source, final ChatModel model) {
        return job(project(decorate, source, brief("en", "uk")), model);
    }

    static SegmentCounts counts(final TestProject project) {
        return Objects.requireNonNull(
                project.stores()
                        .segments()
                        .countsByStatus(project.id(), new AlsoTranslate(false, false, false, false).keptKinds())
                        .data(),
                "counts");
    }

    static SegmentRecord stored(final TestProject project, final String segmentId) {
        return Objects.requireNonNull(
                        project.stores()
                                .segments()
                                .find(project.id(), segmentId)
                                .data(),
                        "find result")
                .orElseThrow();
    }

    static ScriptedChatModel replies(final String... content) {
        final ScriptedChatModel model = new ScriptedChatModel();
        for (final String reply : content) {
            model.answer(Result.ok(new ChatResponse(targetReply(reply), FinishReason.STOP)));
        }
        return model;
    }

    /** A reply cut off by the length limit, which the run flags as {@code validation} without a repair call. */
    static Result<ChatResponse> cutOffReply() {
        return Result.ok(new ChatResponse(targetReply("HE OPENED"), FinishReason.LENGTH));
    }

    static String targetReply(final String target) {
        try {
            return MAPPER.writeValueAsString(Map.of("target", target));
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode scripted target", cause);
        }
    }

    static JobReport report(final Result<JobReport> result) {
        return java.util.Objects.requireNonNull(result.data(), "job report");
    }

    static ExecutorService executor() {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        EXECUTORS.add(executor);
        return executor;
    }

    static <T> T await(final Future<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException cause) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for translation job", cause);
        } catch (Exception cause) {
            future.cancel(true);
            throw new AssertionError("timed out waiting for translation job", cause);
        }
    }

    static void await(final CountDownLatch latch) {
        awaitIgnoringInterrupt(latch, "a test signal");
    }

    static JobEvent await(final LinkedBlockingQueue<JobEvent> events) {
        try {
            final JobEvent event = events.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            return java.util.Objects.requireNonNull(event, "expected job event");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for job event", cause);
        }
    }

    static Paused awaitPaused(final LinkedBlockingQueue<Paused> pauses) {
        try {
            final Paused pause = pauses.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            return java.util.Objects.requireNonNull(pause, "expected pause event");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for pause event", cause);
        }
    }

    static void capturePaused(final LinkedBlockingQueue<Paused> pauses, final JobEvent event) {
        if (event instanceof Paused pause) {
            pauses.add(pause);
        }
    }

    static void shutdown(final ExecutorService executor) {
        EXECUTORS.remove(executor);
        executor.shutdownNow();
        awaitTermination(executor);
    }

    static void shutdownAll() {
        while (!EXECUTORS.isEmpty()) {
            shutdown(java.util.Objects.requireNonNull(EXECUTORS.poll(), "executor"));
        }
    }

    private static void awaitTermination(final ExecutorService executor) {
        try {
            if (!executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("translation job executor did not terminate");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while terminating translation job executor", cause);
        }
    }

    /**
     * Waits like a provider that does not honour an interrupt: the call keeps waiting and returns its answer, which
     * is what the boundary tests need, because they prove what the job does with an answer that arrives anyway.
     */
    static void awaitIgnoringInterrupt(final CountDownLatch latch, final String what) {
        boolean interrupted = false;
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        try {
            while (latch.getCount() > 0) {
                final long left = deadline - System.nanoTime();
                if (left <= 0) {
                    throw new AssertionError("timed out waiting for " + what);
                }
                try {
                    latch.await(left, TimeUnit.NANOSECONDS);
                } catch (InterruptedException cause) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
