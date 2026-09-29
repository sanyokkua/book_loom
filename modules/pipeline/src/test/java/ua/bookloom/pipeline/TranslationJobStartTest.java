package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** Covers where a run starts, what it refuses before any model call, and what it stores. */
class TranslationJobStartTest {

    private static final int PARAGRAPHS = 12;
    private static final int DECIDED_BEFORE_STOP = 5;

    @TempDir
    private Path tempDir;

    // Starting again at the first paragraph would repeat the five decisions the stopped run already stored.
    @Test
    void run_secondRunAfterStop_startsAtFirstPending() {
        final TestProject project = project(twelveParagraphs(), brief("en", "uk"));
        stopAfterFiveDecisions(project);
        final ScriptedChatModel second = replies(upperCased(DECIDED_BEFORE_STOP, PARAGRAPHS));

        job(project, second).run();

        assertThat(second.requests()).hasSize(PARAGRAPHS - DECIDED_BEFORE_STOP);
        assertThat(promptOf(second.requests().getFirst())).contains("\nPara 5.\n");
        assertThat(second.requests())
                .extracting(TranslationJobStartTest::promptOf)
                .noneMatch(prompt -> IntStream.range(0, DECIDED_BEFORE_STOP)
                        .anyMatch(index -> prompt.contains("\nPara " + index + ".\n")));
    }

    // Counting only this run's decisions would report 7 of the 12 segments after the second run.
    @Test
    void run_secondRunOverTwelveSegments_reportsTheWholeProject() {
        final TestProject project = project(twelveParagraphs(), brief("en", "uk"));
        stopAfterFiveDecisions(project);

        final JobReport completed = report(job(project, replies(upperCased(DECIDED_BEFORE_STOP, PARAGRAPHS)))
                .run());

        assertThat(completed)
                .extracting(JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, PARAGRAPHS, PARAGRAPHS, 0);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, PARAGRAPHS, 0, 0, 0));
    }

    // Picking a flagged segment up again would send it to the model a second time and overwrite its flag.
    @Test
    void run_flaggedSegment_notPickedUpBySecondRun() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.");
        final TestProject project = project(source, brief("en", "uk"));
        final TranslationJobImpl first =
                job(project, new ScriptedChatModel().answer(TranslationJobTestSupport.cutOffReply()));
        first.subscribe(event -> cancelAfterFirstDecision(first, event));
        first.run();
        final ScriptedChatModel second = replies("TWO.");

        final JobReport completed = report(job(project, second).run());

        assertThat(second.requests()).hasSize(1);
        assertThat(promptOf(second.requests().getFirst())).contains("\nTwo.\n");
        assertThat(stored(project, "Book.md:0").status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(completed)
                .extracting(JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(2, 1, 1);
        assertThat(completed.flaggedSegments()).containsExactly(new FlaggedSegment("Book.md:0", ErrorCode.validation));
    }

    // Reading a target language that is not a language code into a file name would let ../x leave the folder.
    @Test
    void run_pathLikeTarget_refusedWithoutCall() {
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(project(markdown("One."), brief("en", "../x")), model);

        final Result<JobReport> result = translation.run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Loosening the language grammar would let one of these shapes into the output file name.
    @ParameterizedTest
    @ValueSource(strings = {"e", "engl", "en-", "en_US"})
    void run_malformedTargetLanguage_refusedWithoutCall(final String target) {
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(project(markdown("One."), brief("en", target)), model);

        final Result<JobReport> result = translation.run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // A brief with no target language would name the output after nothing.
    @Test
    void run_nullTargetLanguage_refusedWithoutCall() {
        final ScriptedChatModel model = replies("ONE.");

        final Result<JobReport> result =
                job(project(markdown("One."), brief("en", null)), model).run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // A source language the brief sets is checked like the target language.
    @Test
    void run_malformedSourceLanguage_refusedWithoutCall() {
        final ScriptedChatModel model = replies("ONE.");

        final Result<JobReport> result =
                job(project(markdown("One."), brief("../en", "uk")), model).run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Starting a run for an id nothing stored would translate a book nobody imported.
    @Test
    void run_unknownProject_refusedWithoutCall() {
        final ScriptedChatModel model = replies("ONE.");
        final TestProject project = project(markdown("One."), brief("en", "uk"));

        final Result<JobReport> result = job(project, "missing", model).run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Leaving the auxiliary unit out of the work and the counts keeps the run at the book's own paragraphs.
    @Test
    void run_epubWithAuxiliaryUnit_leavesItOutOfWorkAndCounts() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.", "Two."), List.of("Three.")), "en");
        final TestProject project = project(source, brief("en", "uk"));
        final ScriptedChatModel model = replies("ONE.", "TWO.", "THREE.");

        final JobReport completed = report(job(project, model).run());

        assertThat(model.requests()).hasSize(3);
        assertThat(completed)
                .extracting(JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 3, 0);
        assertThat(stored(project, "aux:title").status()).isEqualTo(SegmentStatus.PENDING);
        assertThat(counts(project).sourceKept()).isPositive();
    }

    // Storing no reason would leave a segment flagged at once with nothing to show the reviewer or the report.
    @Test
    void run_flaggedAtOnce_storesItsReasonAsAReplyFinding() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "One.");
        final TestProject project = project(source, brief("en", "uk"));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("HE OPENED", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse("HE OPENED", FinishReason.STOP)));

        final JobReport completed = report(job(project, model).run());

        final SegmentRecord record = stored(project, "Book.txt:0");
        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.findings())
                .singleElement()
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .containsExactly("validation", Severity.HIGH, "reply");
        assertThat(completed.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:0", ErrorCode.validation));
    }

    private void stopAfterFiveDecisions(final TestProject project) {
        final TranslationJobImpl first = job(project, replies(upperCased(0, DECIDED_BEFORE_STOP)));
        first.subscribe(event -> cancelAfterDecisions(first, event, DECIDED_BEFORE_STOP));
        assertThat(report(first.run()).end()).isEqualTo(JobState.CANCELLED);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(PARAGRAPHS - DECIDED_BEFORE_STOP, 5, 0, 0, 0));
    }

    private Path twelveParagraphs() {
        final String content = IntStream.range(0, PARAGRAPHS)
                .mapToObj(index -> "Para " + index + ".")
                .collect(Collectors.joining("\n\n"));
        return markdown(content);
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static String[] upperCased(final int from, final int to) {
        return IntStream.range(from, to)
                .mapToObj(index -> "PARA " + index + ".")
                .toArray(String[]::new);
    }

    private static String promptOf(final ChatRequest request) {
        return request.messages().stream().map(ChatMessage::content).collect(Collectors.joining("\n"));
    }

    private static void cancelAfterFirstDecision(final TranslationJobImpl translation, final Object event) {
        cancelAfterDecisions(translation, event, 1);
    }

    private static void cancelAfterDecisions(
            final TranslationJobImpl translation, final Object event, final int count) {
        if (event instanceof SegmentDecided decided
                && decided.progress().accepted() + decided.progress().flagged() == count) {
            translation.cancel();
        }
    }
}
