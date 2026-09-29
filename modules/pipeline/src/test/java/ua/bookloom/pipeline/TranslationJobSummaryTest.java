package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.JUDGE;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.judged;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A run refreshes the rolling summary after every twenty accepted segments and at each unit's end, announces each new
 * version, and shows the latest one to every later draft.
 */
class TranslationJobSummaryTest {

    private static final String SUMMARY_BLOCK = "[Book so far — context only; do NOT re-translate it]";
    private static final String SUMMARY = "summary";
    private static final String MODEL_SUMMARY = "Старий чоловік іде до гавані, де стоїть маяк.";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_summary_refreshedAfterTwentyAndAtUnitEnd() {
        final TestProject project = project(lighthouseChapter(), brief("en", "uk"));
        final ScriptedChatModel model = replies(lighthouseReplies());
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = recorded(translation);

        report(translation.run());

        assertThat(memoryEvents(events))
                .containsExactly(
                        new MemoryUpdated(MemoryKind.SUMMARY, "1"), new MemoryUpdated(MemoryKind.SUMMARY, "2"));
        assertThat(decisionsAndMemory(events))
                .containsExactlyElementsOf(Stream.of(
                                Collections.nCopies(20, "decided"),
                                List.of("memory"),
                                List.of("decided"),
                                List.of("memory"))
                        .flatMap(List::stream)
                        .toList());
        assertThat(formats(model)).hasSize(21).containsOnly(DRAFT);
        assertThat(userMessage(model.requests().getFirst())).doesNotContain(SUMMARY_BLOCK);
        assertThat(userMessage(model.requests().get(20))).contains(SUMMARY_BLOCK + "\nThe Lighthouse\n");
        assertThat(Objects.requireNonNull(stored(project, "Book.md:20").context(), "snapshot")
                        .summary())
                .isEqualTo("The Lighthouse");
    }

    @Test
    void run_maxUnitEnd_makesOneSummaryCall() {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), S0 + "\n\n" + S1), brief("en", "uk", QualityDial.MAX));
        final ScriptedChatModel model =
                replies(T0, T1).answerTo(JUDGE, judged()).answerTo(SUMMARY, summaryReply(MODEL_SUMMARY));
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = recorded(translation);

        report(translation.run());

        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, JUDGE, SUMMARY);
        assertThat(memoryEvents(events)).containsExactly(new MemoryUpdated(MemoryKind.SUMMARY, "1"));
        assertThat(latestSummary(project))
                .hasValueSatisfying(summary -> assertThat(summary.target()).isEqualTo(MODEL_SUMMARY));
    }

    @Test
    void run_threeSegments_sendsOnlyTheSummaryUpdate() {
        final TestProject project =
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("Один.", "Два.", "Три."));
        final List<JobEvent> events = recorded(translation);

        report(translation.run());

        assertThat(memoryEvents(events)).containsExactly(new MemoryUpdated(MemoryKind.SUMMARY, "1"));
        assertThat(events.subList(events.size() - 3, events.size()))
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly("SegmentDecided", "MemoryUpdated", "Finished");
        assertThat(events.getLast()).isInstanceOf(Finished.class);
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

    private static List<String> decisionsAndMemory(final List<JobEvent> events) {
        return events.stream()
                .filter(event -> event instanceof SegmentDecided || event instanceof MemoryUpdated)
                .map(event -> event instanceof SegmentDecided ? "decided" : "memory")
                .toList();
    }

    private static Optional<RollingSummary> latestSummary(final TestProject project) {
        return Objects.requireNonNull(
                project.stores().summaries().latest(project.id()).data(), "latest summary");
    }
}
