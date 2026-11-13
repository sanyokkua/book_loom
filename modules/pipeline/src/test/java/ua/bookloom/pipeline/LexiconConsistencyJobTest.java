package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A recurring term a model would render two ways stays one: the person chose «господар», every batch is shown it as a
 * rendering to keep, and no batch is asked which rendering it used (arm 5b retired the key-term asks). The model is a
 * fake that drifts to «учитель» whenever its prompt does not name the established rendering — the behaviour the block
 * exists to stop — and that still reports a {@code terms} object, which the run ignores; it answers at the HTTP-free call
 * seam, so the counts are exact.
 */
class LexiconConsistencyJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final String ESTABLISHED_LINE = "master → господар";
    private static final String P1 = "The master walked to the harbour.";
    private static final String P2 = "The master opened the old door.";
    private static final String P3 = "The boats rested in the harbour.";
    private static final String P4 = "The master smiled at the sea.";
    private static final Map<String, String> TARGETS = Map.of(
            P1, "%s пішов до гавані.",
            P2, "%s відчинив старі двері.",
            P3, "Човни спокійно стояли в гавані.",
            P4, "%s посміхнувся морю.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** Writes «господар» when its prompt names it as established, «учитель» on every call without. */
    private static final class DriftingModel implements ChatModel {

        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String user = userMessage(request);
            final String rendering = user.contains(ESTABLISHED_LINE) ? "господар" : "учитель";
            final List<String> entries = new ArrayList<>();
            final Matcher items = ITEM.matcher(user);
            while (items.find()) {
                entries.add(entry(items.group(1), items.group(2), rendering));
            }
            return Result.ok(new ChatResponse("{\"items\":[" + String.join(",", entries) + "]}", FinishReason.STOP));
        }

        private static String entry(final String id, final String source, final String rendering) {
            final String target = Objects.requireNonNull(TARGETS.get(source)).formatted(capitalised(rendering));
            final String terms = source.contains("master") ? ",\"terms\":{\"master\":\"" + rendering + "\"}" : "";
            return "{\"id\":\"" + id + "\",\"target\":\"" + target + "\"" + terms + "}";
        }

        private static String capitalised(final String word) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
    }

    private TestProject book() {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", P1, P2, P3, P4)), brief("en", "uk"));
        project.stores().lexicon().put(LexiconEntry.of(project.id(), "master").withChosen("господар"));
        return project;
    }

    private static List<LexiconEntry> lexicon(final TestProject project) {
        return Objects.requireNonNull(
                project.stores().lexicon().all(project.id()).data());
    }

    @Test
    void run_modelWouldDriftBetweenBatches_everyBatchIsShownTheEstablishedRenderingAndNoKeyTerms() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 2)
                .run());

        assertThat(model.requests).hasSize(2);
        assertThat(model.requests)
                .extracting(ChunkRunFixtures::userMessage)
                .allSatisfy(user -> assertThat(user)
                        .contains("[Established renderings", ESTABLISHED_LINE)
                        .doesNotContain("[Key terms"));
    }

    @Test
    void run_modelWouldDriftAndReportsTerms_theBookKeepsOneRenderingAndRecordsNoReport() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 2)
                .run());

        assertThat(lexicon(project).getFirst().renderings()).isEmpty();
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 4, 0, 0, 0));
        assertThat(storedTargets(project)).noneMatch(target -> target.contains("Учитель"));
    }

    private static List<String> storedTargets(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().segments().all(project.id()).data())
                .stream()
                .map(SegmentRecord::maskedMachineTarget)
                .filter(Objects::nonNull)
                .toList();
    }

    @Test
    void run_lexiconSnapshotOfADraft_recordsTheRenderingItWasShown() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 2)
                .run());

        final SegmentRecord third = TranslationJobTestSupport.stored(project, "Book.md:3");
        assertThat(third.context()).isNotNull();
        assertThat(Objects.requireNonNull(third.context()).sections())
                .flatExtracting(PromptSection::lines)
                .anySatisfy(line -> assertThat(line).contains(ESTABLISHED_LINE));
    }
}
