package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_TARGET;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * What a run announces as steps on a twelve-paragraph one-unit book whose paragraphs 0 ("***") and 6 ("1/2") are
 * kept verbatim. The chunk token budget is 2000 tokens and the twelve paragraphs hold about a hundred, so the dial's
 * segment cap alone sets the chunks: 8 + 4 on Fast and Balanced, six of 2 on Max.
 */
class TranslationJobStepsTest {

    private static final int PARAGRAPHS = 12;
    private static final String SUMMARY = "summary";
    private static final String SUMMARY_REPLY =
            "{\"summary\":{\"source\":\"Numbered lines.\",\"target\":\"Пронумеровані рядки.\"},\"facts\":[]}";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Ten translatable lines "Line 1." .. "Line 10." between two lines with nothing to translate.
    private TestProject twelveParagraphs(final QualityDial dial) {
        final List<String> paragraphs = IntStream.range(0, PARAGRAPHS)
                .mapToObj(index -> switch (index) {
                    case 0 -> "***";
                    case 6 -> "1/2";
                    default -> "Line " + (index < 6 ? index : index - 1) + ".";
                })
                .toList();
        return project(
                TestBooks.txt(tempDir.resolve("Book.txt"), String.join("\n\n", paragraphs)), brief("en", "uk", dial));
    }

    private static ScriptedChatModel tenDrafts() {
        return replies(IntStream.rangeClosed(1, 10)
                .mapToObj(line -> "Рядок " + line + ".")
                .toArray(String[]::new));
    }

    private static ScriptedChatModel maxModel() {
        final ScriptedChatModel model = tenDrafts();
        IntStream.range(0, 12).forEach(ignored -> model.answerTo(REVIEW, reviewed()));
        return model.answerTo(SUMMARY, Result.ok(new ChatResponse(SUMMARY_REPLY, FinishReason.STOP)));
    }

    private static List<JobEvent> run(final TranslationJobImpl translation) {
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);
        final JobReport report = report(translation.run());
        assertThat(report.end()).as("run ended with %s", report.error()).isEqualTo(JobState.COMPLETED);
        return events;
    }

    private static List<Integer> planned(final List<JobEvent> events, final StepKind kind) {
        return events.stream()
                .filter(StepsPlanned.class::isInstance)
                .map(StepsPlanned.class::cast)
                .filter(plan -> plan.kind() == kind)
                .map(StepsPlanned::added)
                .toList();
    }

    private static List<StepChanged> ended(final List<JobEvent> events, final StepKind kind) {
        return changes(events, kind, StepState.ENDED);
    }

    private static List<StepChanged> changes(final List<JobEvent> events, final StepKind kind, final StepState state) {
        return events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filter(change -> change.kind() == kind && change.state() == state)
                .toList();
    }

    private static int total(final List<JobEvent> events, final StepKind kind) {
        return planned(events, kind).stream().mapToInt(Integer::intValue).sum();
    }

    // The task's Fast row: 12 drafts in chunks of 8 and 4, 2 done at once, and no check, summary or revision.
    @Test
    void run_fastTwelveSegmentsTwoVerbatim_plansTwelveDraftsNoCheck() {
        final List<JobEvent> events = run(job(twelveParagraphs(QualityDial.FAST), tenDrafts()));

        assertThat(planned(events, StepKind.DRAFT)).containsExactly(8, 4);
        assertThat(ended(events, StepKind.DRAFT)).hasSize(12);
        assertThat(ended(events, StepKind.DRAFT))
                .filteredOn(change -> change.result() == StepResult.NO_CALL)
                .extracting(StepChanged::itemRef)
                .containsExactly("Book.txt:0", "Book.txt:6");
        assertThat(planned(events, StepKind.CHECK)).isEmpty();
        assertThat(planned(events, StepKind.SUMMARY)).isEmpty();
        assertThat(planned(events, StepKind.REVISION)).isEmpty();
    }

    @Test
    void run_balancedTwelveSegmentsTwoVerbatim_plansTwelveDraftsTwoChecks() {
        final ScriptedChatModel model = tenDrafts().answerTo(REVIEW, reviewed()).answerTo(REVIEW, reviewed());

        final List<JobEvent> events = run(job(twelveParagraphs(QualityDial.BALANCED), model));

        assertThat(planned(events, StepKind.DRAFT)).containsExactly(8, 4);
        assertThat(planned(events, StepKind.CHECK)).containsExactly(1, 1);
        assertThat(ended(events, StepKind.CHECK)).hasSize(2).allMatch(change -> change.result() == StepResult.DONE);
        assertThat(planned(events, StepKind.SUMMARY)).isEmpty();
        assertThat(planned(events, StepKind.REVISION)).isEmpty();
    }

    // Max: six chunks of 2, two checks each, one summary for the one unit that runs, and the revision as an estimate.
    @Test
    void run_maxTwelveSegments_plansTwelveChecksOneSummaryOneRevision() {
        final List<JobEvent> events = run(job(twelveParagraphs(QualityDial.MAX), maxModel()));

        assertThat(planned(events, StepKind.DRAFT)).containsExactly(2, 2, 2, 2, 2, 2);
        assertThat(planned(events, StepKind.CHECK)).containsExactly(2, 2, 2, 2, 2, 2);
        assertThat(ended(events, StepKind.CHECK)).hasSize(12);
        assertThat(planned(events, StepKind.SUMMARY)).containsExactly(1);
        assertThat(ended(events, StepKind.SUMMARY))
                .extracting(StepChanged::result)
                .containsExactly(StepResult.DONE);
        assertThat(events).contains(new StepsPlanned(StepKind.REVISION, 1, true));
        assertThat(ended(events, StepKind.REVISION)).hasSize(1);
    }

    // The two kept-as-is lines need no call, so their draft steps end the moment they start.
    @Test
    void run_verbatimSegment_draftStepEndsNoCallAtOnce() {
        final List<JobEvent> events = run(job(twelveParagraphs(QualityDial.FAST), tenDrafts()));

        final int firstStarted = events.indexOf(
                changes(events, StepKind.DRAFT, StepState.STARTED).getFirst());
        assertThat(events.subList(firstStarted, firstStarted + 2))
                .containsExactly(
                        new StepChanged(StepKind.DRAFT, "draft", 0, StepState.STARTED, "Book.txt:0", null),
                        new StepChanged(StepKind.DRAFT, "draft", 0, StepState.ENDED, "Book.txt:0", StepResult.NO_CALL));
    }

    // IF a step ended before it started, or twice, THEN the bar would move back or count one step as two.
    @Test
    void run_completed_everyStartedStepEndsOnceInOrder() {
        final List<JobEvent> events = run(job(twelveParagraphs(QualityDial.MAX), maxModel()));

        final Map<String, List<StepState>> statesByStep = events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .collect(Collectors.groupingBy(
                        change -> change.kind() + "#" + change.index(),
                        LinkedHashMap::new,
                        Collectors.mapping(StepChanged::state, Collectors.toList())));
        assertThat(statesByStep).isNotEmpty();
        assertThat(statesByStep.values())
                .allSatisfy(states -> assertThat(states).containsExactly(StepState.STARTED, StepState.ENDED));
        assertThat(Arrays.asList(StepKind.values()))
                .allSatisfy(kind -> assertThat(ended(events, kind)).hasSizeLessThanOrEqualTo(total(events, kind)));
    }

    // A refusal on Fast is repaired by one directed fix: one added step, announced when the round starts.
    @Test
    void run_failingCheck_repairRoundAddsOneStep() {
        final ScriptedChatModel model = replies("I'm sorry, but I can't translate this text.", DOOR_TARGET);
        final TestProject door = project(ChunkRunFixtures.door(tempDir), brief("en", "uk"));

        final List<JobEvent> events = run(job(door, model));

        assertThat(planned(events, StepKind.REPAIR)).containsExactly(1);
        assertThat(events).contains(new StepsPlanned(StepKind.REPAIR, 1, false));
        assertThat(ended(events, StepKind.REPAIR))
                .extracting(StepChanged::itemRef, StepChanged::result)
                .containsExactly(tuple("Book.txt:0", StepResult.DONE));
    }
}
