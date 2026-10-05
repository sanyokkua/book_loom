package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A run refreshes the rolling summary after every twenty accepted segments and at each unit's end, and shows the latest
 * model-written summary to every later draft, announcing it when it changes; the deterministic text is never shown.
 */
class TranslationJobSummaryTest {

    private static final String SUMMARY_BLOCK = "[Book so far — context only; do NOT re-translate it]";
    private static final String SUMMARY = "summary";
    private static final String MODEL_SUMMARY = "Старий чоловік іде до гавані, де стоїть маяк.";
    private static final String EARLIER_SUMMARY = "Раніше: старий чоловік живе біля моря.";
    private static final String GLOSSARY_LINES = "Earth (place, neuter)\nChapter 1. Why Things Fall";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // The deterministic refresh (glossary lines and heading titles) is stored but is not a summary of the story: no
    // draft is shown it and nothing announces it (fixture run: "Earth (place, neuter)…" then chapter titles).
    @Test
    void run_balancedDeterministicRefresh_showsNoSummaryAndAnnouncesNothing() {
        final TestProject project = project(lighthouseChapter(), brief("en", "uk"));
        final ScriptedChatModel model = replies(lighthouseReplies());
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = recorded(translation);

        report(translation.run());

        assertThat(memoryEvents(events)).isEmpty();
        assertThat(formats(model)).hasSize(21).containsOnly(DRAFT);
        assertThat(userMessage(model.requests().get(20))).doesNotContain(SUMMARY_BLOCK);
        assertThat(Objects.requireNonNull(stored(project, "Book.md:20").context(), "snapshot")
                        .summary())
                .isNull();
        assertThat(latestSummary(project)).hasValueSatisfying(summary -> {
            assertThat(summary.version()).isEqualTo(2);
            assertThat(summary.target()).isEmpty();
        });
    }

    // A model-written summary stored by an earlier run is what the next run's drafts are shown, never the deterministic
    // glossary-and-headings text stored beside it.
    @Test
    void run_storedModelSummary_isTheSummaryEveryDraftIsShown() {
        final TestProject project =
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo."), brief("en", "uk"));
        project.stores()
                .summaries()
                .save(new RollingSummary(project.id(), null, GLOSSARY_LINES, MODEL_SUMMARY, 1, null, 0));
        final ScriptedChatModel model = replies("Один.", "Два.");

        report(job(project, model).run());

        assertThat(userMessage(model.requests().getFirst()))
                .contains(SUMMARY_BLOCK + "\n" + MODEL_SUMMARY)
                .doesNotContain(GLOSSARY_LINES);
        assertThat(Objects.requireNonNull(stored(project, "Book.txt:0").context(), "snapshot")
                        .summary())
                .isEqualTo(MODEL_SUMMARY);
    }

    @Test
    void run_storedDeterministicSummaryOnly_showsNoSummary() {
        final TestProject project =
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo."), brief("en", "uk"));
        project.stores().summaries().save(new RollingSummary(project.id(), null, GLOSSARY_LINES, "", 1, null, 0));
        final ScriptedChatModel model = replies("Один.", "Два.");

        report(job(project, model).run());

        assertThat(userMessage(model.requests().getFirst())).doesNotContain(SUMMARY_BLOCK);
        assertThat(Objects.requireNonNull(stored(project, "Book.txt:0").context(), "snapshot")
                        .summary())
                .isNull();
    }

    @Test
    void run_maxUnitEnd_makesOneSummaryCall() {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), S0 + "\n\n" + S1), brief("en", "uk", QualityDial.MAX));
        final ScriptedChatModel model = replies(T0, T1)
                .answerTo(REVIEW, reviewed())
                .answerTo(REVIEW, reviewed())
                .answerTo(SUMMARY, summaryReply(MODEL_SUMMARY));
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = recorded(translation);

        report(translation.run());

        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, REVIEW, REVIEW, SUMMARY);
        assertThat(memoryEvents(events)).containsExactly(new MemoryUpdated(MemoryKind.SUMMARY, "1"));
        assertThat(latestSummary(project))
                .hasValueSatisfying(summary -> assertThat(summary.target()).isEqualTo(MODEL_SUMMARY));
    }

    // Design D3: an empty or over-long summary reply never fails a run; the version written before stays.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"emptyCompletion", "contextWindow"})
    void run_maxUnitEndSummaryUnanswerable_completesAndKeepsThePreviousVersion(final ErrorCode code) {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), S0 + "\n\n" + S1), brief("en", "uk", QualityDial.MAX));
        project.stores()
                .summaries()
                .save(new RollingSummary(project.id(), null, "Earlier.", EARLIER_SUMMARY, 1, null, 0));
        final ScriptedChatModel model = replies(T0, T1)
                .answerTo(REVIEW, reviewed())
                .answerTo(REVIEW, reviewed())
                .answerTo(SUMMARY, Result.err(AppError.of(code, "Unanswerable", "The summary call gave nothing.")));
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = recorded(translation);

        final JobReport report = report(translation.run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.error()).isNull();
        assertThat(report.accepted()).isEqualTo(2);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, REVIEW, REVIEW, SUMMARY);
        assertThat(memoryEvents(events)).isEmpty();
        assertThat(events.getLast()).isInstanceOf(Finished.class);
        assertThat(latestSummary(project)).hasValueSatisfying(summary -> {
            assertThat(summary.version()).isEqualTo(1);
            assertThat(summary.target()).isEqualTo(EARLIER_SUMMARY);
        });
    }

    @Test
    void run_threeSegmentsWithoutAModelSummary_announcesNoSummary() {
        final TestProject project =
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("Один.", "Два.", "Три."));
        final List<JobEvent> events = recorded(translation);

        report(translation.run());

        assertThat(memoryEvents(events)).isEmpty();
        assertThat(events.subList(events.size() - 2, events.size()))
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly("SegmentDecided", "Finished");
    }

    // IF a job resumed on a project with ten accepted segments counted its twenty from zero, THEN the version the
    // twentieth accepted segment earns would only come ten segments later, if the chapter lasted that long.
    @Test
    void run_secondJobOnTenAcceptedSegments_refreshesAfterTheTenthOfItsOwn() {
        final TestProject project =
                project(TestBooks.markdown(tempDir.resolve("Book.md"), numberedLines(26)), brief("en", "uk"));
        final ScriptedChatModel firstModel = replies(numberedReplies(10))
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.")));
        report(job(project, firstModel).run());

        report(job(project, replies(numberedReplies(16))).run());

        assertThat(latestSummary(project)).hasValueSatisfying(summary -> {
            assertThat(summary.version()).isEqualTo(2);
            assertThat(summary.tokensSince()).isEqualTo(6);
        });
    }

    private static String numberedLines(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(line -> "Line " + line + ".")
                .reduce((text, line) -> text + "\n\n" + line)
                .orElseThrow();
    }

    private static String[] numberedReplies(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(line -> "Рядок " + line + ".")
                .toArray(String[]::new);
    }

    /** A heading and twenty short lines: the heading is the only thing the deterministic summary can name. */
    private Path lighthouseChapter() {
        final String lines = IntStream.rangeClosed(1, 20)
                .mapToObj(line -> "Line " + line + ".")
                .reduce("# The Lighthouse", (text, line) -> text + "\n\n" + line);
        return TestBooks.markdown(tempDir.resolve("Book.md"), lines);
    }

    private static String[] lighthouseReplies() {
        return Stream.concat(
                        Stream.of("Маяк на скелі"),
                        IntStream.rangeClosed(1, 20).mapToObj(line -> "Рядок " + line + "."))
                .toArray(String[]::new);
    }

    private static Result<ChatResponse> summaryReply(final String target) {
        return Result.ok(new ChatResponse(
                "{\"summary\":{\"source\":\"An old man walks to the lighthouse.\",\"target\":\"" + target
                        + "\"},\"facts\":[]}",
                FinishReason.STOP));
    }

    private static List<JobEvent> recorded(final TranslationJobImpl translation) {
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);
        return events;
    }

    private static List<MemoryUpdated> memoryEvents(final List<JobEvent> events) {
        return events.stream()
                .filter(MemoryUpdated.class::isInstance)
                .map(MemoryUpdated.class::cast)
                .toList();
    }

    private static Optional<RollingSummary> latestSummary(final TestProject project) {
        return Objects.requireNonNull(
                project.stores().summaries().latest(project.id()).data(), "latest summary");
    }
}
