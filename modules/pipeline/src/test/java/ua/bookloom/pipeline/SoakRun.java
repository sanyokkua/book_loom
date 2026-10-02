package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.run.RunStores;

/**
 * One soak run: imports a book through the real project service, runs the real job over the fault-injecting model on a
 * scripted clock — so every recovery wait and every watchdog ceiling passes at once — exports it through the real
 * export service, opens the export again, and measures memory, threads and time on the way.
 */
final class SoakRun {

    private static final Duration PAST_THE_CEILING =
            FaultyModel.HANG_TIMEOUT.multipliedBy(3).dividedBy(2).plusSeconds(1);

    private final Injector injector = Guice.createInjector(
            new DocumentModule(),
            new LlmModule(),
            new PersistenceModule(),
            new PipelineModule(),
            new ReviewModeTestModule());
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScriptedClock clock = new ScriptedClock();
    private final AtomicReference<@Nullable Runnable> tick = new AtomicReference<>();

    /**
     * What a run left behind.
     *
     * @param report the job's report
     * @param counts the stored statuses after the run
     * @param documentSegments how many segments the source book holds
     * @param export what the export wrote
     * @param exportedSegments how many segments the exported book holds when opened again
     * @param injected how many times each fault was injected
     * @param calls how many model calls were made
     * @param events what the run announced
     * @param heap the retained heap at each quarter of the run
     * @param threadsBefore live threads before the run
     * @param threadsAfter live threads after the export
     * @param elapsed real time the run and the export took
     * @param errors the ERROR lines no injected fault explains
     * @param virtual how far the run's clock moved
     */
    record Outcome(
            JobReport report,
            SegmentCounts counts,
            int documentSegments,
            ExportReport export,
            int exportedSegments,
            Map<FaultyModel.Fault, Integer> injected,
            int calls,
            SoakEvents events,
            HeapSamples heap,
            int threadsBefore,
            int threadsAfter,
            Duration elapsed,
            java.util.List<String> errors,
            Duration virtual) {}

    Outcome run(final Path book, final FaultPlan plan, final Path exportTo) {
        final String projectId = importBook(book);
        final FaultyModel model = new FaultyModel(mapper, clock, plan);
        model.stallWith(this::stallNow);
        final TranslationJobImpl job = job(projectId, model);
        job.recoverWith(model.probe());
        final HeapSamples heap = new HeapSamples();
        final SoakEvents events = new SoakEvents(heap);
        job.subscribe(events);
        return measured(new Prepared(projectId, book, exportTo, model, job, events, heap));
    }

    /** A run set up and not yet started. */
    private record Prepared(
            String projectId,
            Path book,
            Path exportTo,
            FaultyModel model,
            TranslationJobImpl job,
            SoakEvents events,
            HeapSamples heap) {}

    private Outcome measured(final Prepared run) {
        final int threadsBefore = liveThreads();
        run.heap().baseline();
        try (SoakLog log = SoakLog.attach()) {
            final long start = System.nanoTime();
            final JobReport report = dataOf(run.job().run());
            final ExportReport export = export(run.projectId(), run.exportTo());
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            run.heap().end();
            return new Outcome(
                    report,
                    counts(run.projectId()),
                    segmentsOf(run.book()),
                    export,
                    segmentsOf(run.exportTo()),
                    run.model().injected(),
                    run.model().calls(),
                    run.events(),
                    run.heap(),
                    threadsBefore,
                    liveThreads(),
                    elapsed,
                    log.unexpectedErrors(),
                    clock.sinceStart());
        }
    }

    private String importBook(final Path book) {
        final ProjectService projects = injector.getInstance(ProjectService.class);
        final ImportedBook imported = dataOf(projects.importBook(book));
        final String projectId = Objects.requireNonNull(imported.projectId(), "project id");
        dataOf(projects.updateBrief(projectId, balancedUkrainian(Objects.requireNonNull(imported.brief(), "brief"))));
        return projectId;
    }

    private TranslationJobImpl job(final String projectId, final FaultyModel model) {
        final TranslationJobImpl job = new TranslationJobImpl(
                injector.getInstance(DocumentPort.class),
                new RunRequest(projectId, ReviewMode.UNATTENDED),
                model,
                mapper,
                injector.getInstance(PromptTemplates.class),
                stores(),
                injector.getInstance(QualityLoop.class),
                injector.getInstance(SentenceSplitter.class),
                injector.getInstance(ConsistencyPass.class),
                clock,
                delay -> {
                    clock.advance(delay);
                    return 0;
                },
                (period, ticking) -> {
                    tick.set(ticking);
                    return () -> tick.set(null);
                });
        job.pauseAt(Set.of(PausePoint.ON_ERROR));
        return job;
    }

    // Runs on the job thread inside the hanging call: the tick interrupts that very thread, as it would from its own.
    private void stallNow() {
        clock.advance(PAST_THE_CEILING);
        Objects.requireNonNull(tick.get(), "the watchdog ticks while the run runs")
                .run();
    }

    private RunStores stores() {
        return new RunStores(
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(CheckpointPort.class),
                injector.getInstance(OpenProjects.class),
                injector.getInstance(RunRepository.class),
                injector.getInstance(GlossaryRepository.class),
                injector.getInstance(TmRepository.class),
                injector.getInstance(SummaryRepository.class));
    }

    private ExportReport export(final String projectId, final Path destination) {
        return dataOf(dataOf(injector.getInstance(ExportService.class)
                        .newExport(new ExportRequest(projectId, destination, false, Set.of(), false), null))
                .run());
    }

    private SegmentCounts counts(final String projectId) {
        final BookBrief brief = Objects.requireNonNull(
                dataOf(injector.getInstance(ProjectRepository.class).find(projectId))
                        .orElseThrow()
                        .brief(),
                "brief");
        return dataOf(injector.getInstance(SegmentRepository.class)
                .countsByStatus(projectId, brief.alsoTranslate().keptKinds()));
    }

    private int segmentsOf(final Path book) {
        final DocumentPort documents = injector.getInstance(DocumentPort.class);
        final Document document = dataOf(documents.open(book));
        try {
            return document.units().stream()
                    .mapToInt(unit -> unit.segments().size())
                    .sum();
        } finally {
            documents.close(document);
        }
    }

    private static int liveThreads() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    private static BookBrief balancedUkrainian(final BookBrief brief) {
        return new BookBrief(
                brief.sourceLanguage() == null ? "en" : brief.sourceLanguage(),
                "uk",
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
                QualityDial.BALANCED);
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected data, got " + result.error());
    }
}
