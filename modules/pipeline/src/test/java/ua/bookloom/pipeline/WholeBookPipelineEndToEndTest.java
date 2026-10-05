package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.WholeBookRun.BookRun;

/**
 * The one test that proves the parts of a run are connected at the real HTTP seam, in both provider dialects: a
 * nine-paragraph Markdown book goes through preparation, drafting (the first chunk's eight paragraphs in one batch
 * call), the chunk's reviewer, a directed fix for a refused edit that resolves it, a fix that answers the source
 * and leaves the issue open so the segment is flagged, the Assisted pause, the person's edit, the resume, the
 * unit's end and an export with every side file. Every stubbed Ukrainian target is Cyrillic with a length ratio inside
 * 0.81–1.69 of its source, so only the stubbed echo fails a deterministic check (the ratios are listed in
 * {@link WholeBookRun}).
 */
// `slow`: a whole run end to end; `test` and the gate run it, the `fastTest` inner loop leaves it out.
@Tag("slow")
class WholeBookPipelineEndToEndTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String DOOR = "He opened the old door.";
    private static final String DOOR_TARGET = "Він відчинив старі двері.";
    private static final String HOUSE = "She left the house early.";
    private static final String HOUSE_TARGET = "Вона рано вийшла з дому.";
    private static final int UNAVAILABLE = 503;
    private static final int PARAGRAPHS = 50;
    private static final int SEA_CHUNK = 8;
    private static final int SEA_BATCHES = 7;
    private static final String SEA_TARGET = "Том дивився на сіре море тієї ночі.";
    // Measured: about 4,280 events at DEBUG or higher in each dialect once every attempt and context was announced, 14
    // of them at INFO or higher (the seven batch drafts each log one INFO line); the bound is the next multiple of 500
    // above the first count.
    private static final int DEBUG_OR_HIGHER_BOUND = 4_500;
    private static final int INFO_OR_HIGHER_BOUND = 30;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // The chunk's one batch draft and its reviewer come before any decision; the two fixes follow, and the flagged
    // eighth segment stops the run before the next chunk's draft. A call about several segments — the batch draft of
    // eight, the chunk's reviewer over eight — names none; a call about one segment names it.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_assistedBalancedBook_callsInTheStubbedOrder(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.calls())
                .containsExactly(
                        "DRAFT null",
                        "REVIEW null",
                        "DIRECTED_FIX Book.md:1",
                        "DIRECTED_FIX Book.md:7",
                        "DRAFT Book.md:8",
                        "REVIEW Book.md:8");
    }

    // A pause on the flagged segment after exactly four requests, with the whole first chunk already stored.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_assistedBalancedBook_pausesOnFlaggedAfterFourRequests(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.pause())
                .extracting(Paused::reason, Paused::segmentId)
                .containsExactly(PauseReason.ON_FLAGGED, "Book.md:7");
        assertThat(run.requestsAtPause()).isEqualTo(4);
        assertThat(run.statusesAtPause())
                .containsExactly(
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.ACCEPTED,
                        SegmentStatus.FLAGGED,
                        SegmentStatus.PENDING);
    }

    // The refused edit's directed fix names the reviewer's quote, and its result — which no longer holds it — is
    // accepted.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_directedFixForARefusedEdit_namesTheQuoteAndAcceptsTheSegmentAsRepaired(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.segment("Book.md:1"))
                .extracting(SegmentView::status, SegmentView::path, SegmentView::maskedMachineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, WholeBookRun.FIX_1);
        assertThat(WholeBookRun.userMessage(run.bodies().get(2))).contains("пішла", "meaning");
    }

    // The batch of eight short paragraphs is capped by its items' allowances; the reviewer of eight pairs by its pairs.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_batchAndReview_capTheBatchByItsItemsAndTheReviewerByItsPairs(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.bodies().get(0)).contains(capField(kind) + ":507");
        assertThat(run.bodies().get(1)).contains(capField(kind) + ":937");
    }

    // The edit saved during the pause is the person's text, and the next chunk's draft reads it as a preceding target.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void resume_afterTheEditOfTheFlaggedSegment_feedsItToTheNextChunkAndCompletes(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.edited())
                .extracting(SegmentView::status, SegmentView::userTarget)
                .containsExactly(SegmentStatus.REVISED, WholeBookRun.EDIT);
        assertThat(WholeBookRun.precedingTargets(run.bodies().get(4))).contains(WholeBookRun.EDIT);
        assertThat(run.bodies()).hasSize(6);
        assertThat(run.report().end()).isEqualTo(JobState.COMPLETED);
        assertThat(run.runState()).isEqualTo(JobState.COMPLETED);
        assertThat(run.segment("Book.md:8").status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    // Preparation proposes the thrice-named Hale once; the unit's end holds it and summarises without a model call.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_nameInThreeMidSentences_isProposedOnceAndTheUnitEndSummarisesWithoutACall(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.glossary())
                .singleElement()
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly("Hale", null, false);
        assertThat(memoryUpdates(run.events(), MemoryKind.GLOSSARY)).containsExactly("+1");
        assertThat(memoryUpdates(run.events(), MemoryKind.SUMMARY)).isEmpty();
        assertThat(run.calls()).noneMatch(call -> call.startsWith(CallKind.SUMMARY.name()));
    }

    // The batch draft's reply carries the dialect's own usage fields, which reach its finished-call event as reported;
    // the call is about eight segments, so it names none of them alone.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_replyWithUsage_finishesItsCallWithTheReportedTokens(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.events())
                .filteredOn(ModelCallFinished.class::isInstance)
                .map(ModelCallFinished.class::cast)
                .first()
                .extracting(
                        ModelCallFinished::segmentId,
                        finished -> WholeBookRun.usage(finished).prompt(),
                        finished -> WholeBookRun.usage(finished).completion(),
                        ModelCallFinished::usageEstimated)
                .containsExactly(null, 120, 45, false);
    }

    // The exported book holds the accepted, repaired and edited targets with the emphasis restored.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void export_afterTheRun_writesEveryDecidedTarget(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(WholeBookRun.read(tempDir.resolve("Book.uk.md")))
                .contains(
                        WholeBookRun.DRAFT_0,
                        WholeBookRun.FIX_1,
                        "Вони чекали на *Гейла* до самого ранку.",
                        WholeBookRun.EDIT,
                        WholeBookRun.DRAFT_4);
        assertThat(run.exported().destination()).isEqualTo(tempDir.resolve("Book.uk.md"));
    }

    // Every chosen side file is written beside the book: the glossary, the offline bilingual page and the report. The
    // report counts Book.md:0, :2 to :6 and :8 as accepted without review — the repaired :1 is counted apart — and the
    // edited :7 as reviewed.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void export_allThreeSideFiles_writesThemBesideTheBook(final ProviderKind kind) {
        final BookRun run = WholeBookRun.run(kind, tempDir);

        assertThat(run.exported().sideFiles())
                .containsExactlyInAnyOrder(
                        tempDir.resolve("Book.uk.glossary.csv"),
                        tempDir.resolve("Book.uk.bilingual.html"),
                        tempDir.resolve("Book.uk.report.md"));
        assertThat(WholeBookRun.read(tempDir.resolve("Book.uk.glossary.csv")))
                .startsWith("term,target,type,gender,locked")
                .containsPattern("(?m)^Hale,");
        final String bilingual = WholeBookRun.read(tempDir.resolve("Book.uk.bilingual.html"));
        assertThat(bilingual.split("<tr><td>", -1)).hasSize(10);
        assertThat(bilingual).doesNotContain("http");
        assertThat(WholeBookRun.read(tempDir.resolve("Book.uk.report.md")))
                .contains(
                        "- Written with a translation: 9",
                        "- Accepted without review: 7",
                        "- Reviewed by you: 1",
                        "- Body segments re-opened and verified: 9");
    }

    // Three 503 answers use up the client's attempts; the run pauses with upstream and redoes the call on resume.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_providerAnswers503UntilRetriesRunOut_pausesOnErrorAndCompletesAfterResume(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.failure(UNAVAILABLE),
                    provider.failure(UNAVAILABLE),
                    provider.failure(UNAVAILABLE),
                    provider.reply(WholeBookRun.batchReply(List.of(DOOR_TARGET, HOUSE_TARGET)), Duration.ZERO)));
            final WholeBookRun.Project project = WholeBookRun.project(
                    TestBooks.markdown(tempDir.resolve("Book.md"), DOOR + "\n\n" + HOUSE), QualityDial.FAST);
            final TranslationJob job =
                    project.job(ReviewMode.UNATTENDED, provider.model(REQUEST_TIMEOUT, ignored -> {}));
            final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
            job.subscribe(event -> capturePaused(pauses, event));
            job.pauseAt(Set.of(PausePoint.ON_ERROR));

            final Future<Result<JobReport>> running = executor().submit(job::run);
            final Paused pause = awaitPaused(pauses);
            final int requestsAtPause = provider.chatRequests();
            job.resume();
            final JobReport ended = report(await(running));

            assertPausedOnUpstreamBeforeAnySegment(pause);
            assertThat(requestsAtPause).isEqualTo(3);
            assertThat(ended).extracting(JobReport::end, JobReport::accepted).containsExactly(JobState.COMPLETED, 2);
            assertThat(provider.chatRequests()).isEqualTo(4);
        }
    }

    // Book text is written only at TRACE, and the lines at the usual levels stay few enough to keep every run logged.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_fiftyParagraphs_logsBookTextAtTraceOnlyWithinTheVolumeBound(final ProviderKind kind) {
        final List<ILoggingEvent> logged = logAtTrace(() -> runSeaBook(kind));

        final List<String> usual = messages(logged, Level.DEBUG);
        assertThat(usual).noneMatch(line -> line.contains("Tom watched") || line.contains("Том дивився"));
        assertThat(usual).noneMatch(line -> line.contains("night"));
        assertThat(messages(logged, Level.TRACE)).anyMatch(line -> line.contains("Tom watched"));
        assertThat(usual).hasSizeLessThan(DEBUG_OR_HIGHER_BOUND);
        assertThat(messages(logged, Level.INFO)).hasSizeLessThanOrEqualTo(INFO_OR_HIGHER_BOUND);
    }

    private static void assertPausedOnUpstreamBeforeAnySegment(final Paused pause) {
        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.upstream);
        assertThat(pause.progress())
                .extracting(JobProgress::accepted, JobProgress::pending)
                .containsExactly(0, 2);
    }

    private void runSeaBook(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            // Fifty paragraphs in Fast chunks of eight: six full batches and one of two, each answered whole.
            provider.stubSequence(IntStream.range(0, SEA_BATCHES)
                    .map(batch -> batch < SEA_BATCHES - 1 ? SEA_CHUNK : PARAGRAPHS % SEA_CHUNK)
                    .mapToObj(size -> provider.reply(
                            WholeBookRun.batchReply(Collections.nCopies(size, SEA_TARGET)), Duration.ZERO))
                    .toList());
            final String book = IntStream.rangeClosed(1, PARAGRAPHS)
                    .mapToObj(night -> "Tom watched the grey sea on night " + night + ".")
                    .collect(Collectors.joining("\n\n"));
            final WholeBookRun.Project project =
                    WholeBookRun.project(TestBooks.markdown(tempDir.resolve("Book.md"), book), QualityDial.FAST);
            final JobReport ended =
                    report(project.job(ReviewMode.UNATTENDED, provider.model(REQUEST_TIMEOUT, ignored -> {}))
                            .run());
            assertThat(ended).extracting(JobReport::end, JobReport::accepted).containsExactly(JobState.COMPLETED, 50);
        }
    }

    /** Runs {@code action} with every BookLoom logger at TRACE, answering what they logged meanwhile. */
    private static List<ILoggingEvent> logAtTrace(final Runnable action) {
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        final Level previous = bookloom.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        bookloom.addAppender(appender);
        bookloom.setLevel(Level.TRACE);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            bookloom.setLevel(previous);
            bookloom.detachAppender(appender);
            appender.stop();
        }
    }

    private static List<String> messages(final List<ILoggingEvent> logged, final Level atLeast) {
        return logged.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(atLeast))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static List<String> memoryUpdates(final List<JobEvent> events, final MemoryKind kind) {
        return events.stream()
                .filter(MemoryUpdated.class::isInstance)
                .map(MemoryUpdated.class::cast)
                .filter(update -> update.kind() == kind)
                .map(MemoryUpdated::label)
                .toList();
    }

    private static String capField(final ProviderKind kind) {
        return kind == ProviderKind.OLLAMA ? "\"num_predict\"" : "\"max_tokens\"";
    }
}
