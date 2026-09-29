package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.targetReply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.PersistenceModule;

/**
 * The whole-book scenario {@link WholeBookPipelineEndToEndTest} checks facet by facet: the real document, persistence
 * and pipeline modules wired as the app wires them, the WireMock-backed model passed to the run, and the eleven
 * provider answers stubbed in the order the engine asks for them.
 *
 * <p>Code-point lengths of each stubbed target against its source (D-3 needs 0.81–1.69 and Cyrillic):
 * {@code Book.md:0} 32/37 = 0.86; {@code Book.md:1} 35/40 = 0.88 and its fix 37/40 = 0.93; {@code Book.md:2} 45/47 =
 * 0.96 in masked form; {@code Book.md:3} 26/26 = 1.00 and the edit 30/26 = 1.15; {@code Book.md:4} 30/35 = 0.86. The
 * fourth segment's fixes answer its English source, which the echo check fails outright.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WholeBookRun {

    static final String DRAFT_0 = "Двері відчинив Гейл пізно вночі.";
    static final String DRAFT_1 = "Вона пішла з Гейлом до темної зали.";
    static final String DRAFT_2 = "Вони чекали на ⟦g0⟧Гейла⟦g1⟧ до самого ранку.";
    static final String DRAFT_3 = "Він пішов з дому вдосвіта.";
    static final String FIX_1 = "Вона пройшла з Гейлом до темної зали.";
    static final String EDIT = "Він вийшов з дому на світанку.";
    static final String DRAFT_4 = "Того вечора дощ так і не вщух.";

    private static final String SOURCE_3 = "He left the house at dawn.";
    private static final String BOOK = String.join(
            "\n\n",
            "The door was opened by Hale at night.",
            "She walked with Hale into the dark hall.",
            "They waited for *Hale* until the morning.",
            SOURCE_3,
            "The rain did not stop that evening.");
    private static final String CHUNK_JUDGE = "{\"score\":0.9,\"verdict\":\"revise\",\"findings\":["
            + "{\"segmentId\":\"s2\",\"type\":\"omission\",\"severity\":\"medium\",\"note\":\"drops a clause\"},"
            + "{\"segmentId\":\"s4\",\"type\":\"meaning\",\"severity\":\"high\",\"note\":\"wrong time\"}],"
            + "\"deferrals\":[]}";
    private static final String RE_JUDGE = "{\"score\":0.9,\"verdict\":\"accept\"}";
    private static final String LAST_JUDGE = "{\"score\":0.92,\"verdict\":\"accept\",\"findings\":[],\"deferrals\":[]}";
    private static final int PROMPT_TOKENS = 120;
    private static final int COMPLETION_TOKENS = 45;
    private static final long EVAL_NANOS = 1_500_000_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int SEGMENTS = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** What the scenario left behind, read at the pause and after the export. */
    record BookRun(
            Paused pause,
            int requestsAtPause,
            List<SegmentStatus> statusesAtPause,
            SegmentView edited,
            JobReport report,
            JobState runState,
            List<JobEvent> events,
            List<String> bodies,
            List<GlossaryEntry> glossary,
            List<SegmentView> segments,
            ExportReport exported) {

        SegmentView segment(final String segmentId) {
            return segments.stream()
                    .filter(view -> view.segmentId().equals(segmentId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no segment " + segmentId));
        }

        /** The kind and segment of every model call the run started, in order. */
        List<String> calls() {
            return events.stream()
                    .filter(ModelCallStarted.class::isInstance)
                    .map(ModelCallStarted.class::cast)
                    .map(started -> started.kind() + " " + started.segmentId())
                    .toList();
        }
    }

    /** An imported project on the real modules, and the services that act on it. */
    record Project(Injector injector, String id) {

        TranslationJob job(final ReviewMode mode, final ChatModel model) {
            return dataOf(injector.getInstance(TranslationEngine.class).newJob(new RunRequest(id, mode), model));
        }

        ReviewDesk desk() {
            return injector.getInstance(ReviewDesk.class);
        }
    }

    static Project project(final Path book, final QualityDial dial) {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new LlmModule(),
                new PersistenceModule(),
                new PipelineModule(),
                new ReviewModeTestModule());
        final ProjectService projects = injector.getInstance(ProjectService.class);
        final ImportedBook imported = dataOf(projects.importBook(book));
        final String id = Objects.requireNonNull(imported.projectId(), "project id");
        dataOf(projects.updateBrief(id, TranslationJobTestSupport.brief("en", "uk", dial)));
        return new Project(injector, id);
    }

    /** The five-paragraph book, three of whose paragraphs name Hale mid-sentence, written as {@code Book.md}. */
    static Path book(final Path directory) {
        return TestBooks.markdown(directory.resolve("Book.md"), BOOK);
    }

    /** Runs the whole scenario against a fresh WireMock server speaking {@code kind}. */
    static BookRun run(final ProviderKind kind, final Path directory) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(replies(provider));
            final Project project = project(book(directory), QualityDial.BALANCED);
            final TranslationJob job = project.job(ReviewMode.ASSISTED, provider.model(REQUEST_TIMEOUT, ignored -> {}));
            final List<JobEvent> events = new CopyOnWriteArrayList<>();
            final LinkedBlockingQueue<Paused> pauses = recordEvents(job, events);

            final Future<Result<JobReport>> running = executor().submit(job::run);
            final Paused pause = awaitPaused(pauses);
            assertFlaggedPause(pause);
            final int requestsAtPause = provider.chatRequests();
            final List<SegmentStatus> statusesAtPause = statuses(project);
            final SegmentView edited = editTheFlaggedSegment(project);
            job.resume();
            final JobReport ended = report(await(running));
            return new BookRun(
                    pause,
                    requestsAtPause,
                    statusesAtPause,
                    edited,
                    ended,
                    runState(project),
                    List.copyOf(events),
                    provider.chatBodies(),
                    dataOf(project.injector().getInstance(GlossaryService.class).entries(project.id())),
                    segments(project),
                    export(project, directory.resolve("Book.uk.md")));
        }
    }

    /** A pause for any other reason would mean the stubbed sequence was disturbed, so say what it was. */
    private static void assertFlaggedPause(final Paused pause) {
        assertThat(pause.reason()).as("pause error: %s", pause.error()).isEqualTo(PauseReason.ON_FLAGGED);
    }

    /** Keeps every event the job announces in {@code events} and answers the queue its pauses arrive on. */
    private static LinkedBlockingQueue<Paused> recordEvents(final TranslationJob job, final List<JobEvent> events) {
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        job.subscribe(event -> {
            events.add(event);
            capturePaused(pauses, event);
        });
        return pauses;
    }

    /** Saves the person's edit of the flagged Book.md:3 through the review desk, as the review panel would. */
    private static SegmentView editTheFlaggedSegment(final Project project) {
        dataOf(project.desk().saveEdit(project.id(), "Book.md:3", EDIT));
        return view(project, "Book.md:3");
    }

    /** The eleven answers, in the order the engine is predicted to ask. */
    private static List<ResponseDefinitionBuilder> replies(final WireMockProvider provider) {
        return List.of(
                provider.replyWithUsage(targetReply(DRAFT_0), PROMPT_TOKENS, COMPLETION_TOKENS, EVAL_NANOS),
                provider.target(DRAFT_1, Duration.ZERO),
                provider.target(DRAFT_2, Duration.ZERO),
                provider.target(DRAFT_3, Duration.ZERO),
                provider.reply(CHUNK_JUDGE, Duration.ZERO),
                provider.target(FIX_1, Duration.ZERO),
                provider.reply(RE_JUDGE, Duration.ZERO),
                provider.target(SOURCE_3, Duration.ZERO),
                provider.target(SOURCE_3, Duration.ZERO),
                provider.target(DRAFT_4, Duration.ZERO),
                provider.reply(LAST_JUDGE, Duration.ZERO));
    }

    private static ExportReport export(final Project project, final Path destination) {
        final ExportRequest request = new ExportRequest(
                project.id(),
                destination,
                false,
                Set.of(SideFile.GLOSSARY_CSV, SideFile.BILINGUAL_HTML, SideFile.QUALITY_REPORT),
                false);
        return dataOf(dataOf(project.injector().getInstance(ExportService.class).newExport(request, null))
                .run());
    }

    private static List<SegmentStatus> statuses(final Project project) {
        return IntStream.range(0, SEGMENTS)
                .mapToObj(index -> view(project, "Book.md:" + index).status())
                .toList();
    }

    private static List<SegmentView> segments(final Project project) {
        return IntStream.range(0, SEGMENTS)
                .mapToObj(index -> view(project, "Book.md:" + index))
                .toList();
    }

    private static SegmentView view(final Project project, final String segmentId) {
        return dataOf(project.desk().segment(project.id(), segmentId));
    }

    private static JobState runState(final Project project) {
        return dataOf(project.injector().getInstance(RunRepository.class).latest(project.id()))
                .orElseThrow()
                .state();
    }

    /** The user message of a chat request body, the same field in both dialects. */
    static String userMessage(final String body) {
        try {
            return MAPPER.readTree(body)
                    .path("messages")
                    .path(1)
                    .path("content")
                    .asText();
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not read request body", cause);
        }
    }

    /** The text between the draft prompt's preceding-translations tags, or empty when it has none. */
    static String precedingTargets(final String body) {
        final String message = userMessage(body);
        final int start = message.indexOf("<PreviousTranslations>");
        final int end = message.indexOf("</PreviousTranslations>");
        return start < 0 || end < start ? "" : message.substring(start, end);
    }

    static TokenUsage usage(final ModelCallFinished finished) {
        return Objects.requireNonNull(finished.usage(), "usage");
    }

    static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException cause) {
            throw new AssertionError("could not read " + path, cause);
        }
    }

    static <T> T dataOf(final Result<T> result) {
        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "result data");
    }
}
