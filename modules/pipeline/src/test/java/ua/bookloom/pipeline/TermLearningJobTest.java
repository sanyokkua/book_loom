package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A recurring term is learned from the decided segments with no model call: the model here never returns a
 * {@code terms} reply, writes «господар» for {@code master} three times and then drifts to «учитель» unless its prompt
 * names the rendering to keep. After the third chapter is stored the next prompt names it, so the fourth chapter keeps
 * it, and the metric reads one rendering for the term. Each chapter is one chunk, so a chunk's pairs are learned
 * before the next chunk is read.
 */
class TermLearningJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final Pattern SHOWN = Pattern.compile("<Text>\\n(.*?)\\n</Text>", Pattern.DOTALL);
    private static final String ESTABLISHED_LINE = "master → господар";
    private static final String P4 = "The master sat in the dark.";
    private static final List<List<String>> CHAPTERS = List.of(
            List.of("The master walked to the harbour.", "The boats rested in the harbour."),
            List.of("The master opened the old door.", "The lamp burned all night."),
            List.of("The master smiled at the sea.", "The wind rose over the hills."),
            List.of(P4, "The bell rang twice."));
    private static final Map<String, String> TARGETS = Map.of(
            "The master walked to the harbour.",
            "%s пішов до гавані.",
            "The boats rested in the harbour.",
            "Човни спокійно стояли біля причалу.",
            "The master opened the old door.",
            "%s відчинив старі двері.",
            "The lamp burned all night.",
            "Лампа горіла цілу ніч.",
            "The master smiled at the sea.",
            "%s посміхнувся морю.",
            "The wind rose over the hills.",
            "Вітер здійнявся над пагорбами.",
            P4,
            "%s сів у темряві.",
            "The bell rang twice.",
            "Двічі продзвенів дзвін.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** Never reports terms; drifts to «учитель» in the fourth chapter unless its prompt names «господар». */
    private static final class DriftingModel implements ChatModel {

        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String user = userMessage(request);
            final String rendering = user.contains(P4) && !user.contains(ESTABLISHED_LINE) ? "учитель" : "господар";
            final Matcher items = ITEM.matcher(user);
            final List<String> entries = new ArrayList<>();
            while (items.find()) {
                entries.add(
                        "{\"id\":\"" + items.group(1) + "\",\"target\":\"" + target(items.group(2), rendering) + "\"}");
            }
            final String reply = !entries.isEmpty()
                    ? "{\"items\":[" + String.join(",", entries) + "]}"
                    : TranslationJobTestSupport.targetReply(target(shown(user), rendering));
            return Result.ok(new ChatResponse(reply, FinishReason.STOP));
        }

        private static String shown(final String user) {
            final Matcher matcher = SHOWN.matcher(user);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static String target(final String source, final String rendering) {
            final String word = Character.toUpperCase(rendering.charAt(0)) + rendering.substring(1);
            return Objects.requireNonNull(TARGETS.get(source)).formatted(word);
        }

        private boolean namesRenderingFor(final String source) {
            return requests.stream()
                    .filter(request -> userMessage(request).contains(source))
                    .anyMatch(request -> userMessage(request).contains(ESTABLISHED_LINE));
        }
    }

    private TestProject book() {
        final TestProject project =
                project(TestBooks.epub(tempDir.resolve("Book.epub"), CHAPTERS, "en"), brief("en", "uk"));
        project.stores().lexicon().put(LexiconEntry.of(project.id(), "master"));
        return project;
    }

    private static LexiconEntry master(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().lexicon().all(project.id()).data())
                .getFirst();
    }

    private static List<String> storedTargets(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().segments().all(project.id()).data())
                .stream()
                .map(SegmentRecord::machineTarget)
                .filter(Objects::nonNull)
                .toList();
    }

    private void assertLearnedAfterThreeChapters(final DriftingModel model, final TestProject project) {
        for (final String source : List.of(
                CHAPTERS.get(0).getFirst(),
                CHAPTERS.get(1).getFirst(),
                CHAPTERS.get(2).getFirst())) {
            assertThat(model.namesRenderingFor(source)).as(source).isFalse();
        }
        assertThat(model.namesRenderingFor(P4)).isTrue();
        assertThat(model.requests.stream()
                        .filter(request -> userMessage(request).contains(P4))
                        .map(ChatRequest::messages)
                        .flatMap(List::stream)
                        .map(message -> message.content())
                        .anyMatch(content -> content.contains("[Established renderings")))
                .isTrue();
        assertThat(storedTargets(project)).noneMatch(target -> target.contains("Учитель"));
    }

    @Test
    void run_batchedModelNeverReportsTerms_theFourthChapterIsShownTheLearnedRendering() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED))
                .run());

        assertLearnedAfterThreeChapters(model, project);
        assertThat(master(project).renderings()).isEmpty();
        assertThat(master(project).learned()).isEqualTo(new LexiconEntry.Learned("господар", 4, 4));
        final RenderingConsistency metric = RenderingConsistency.of(List.of(master(project)));
        assertThat(metric.distinctPerTerm()).isEqualTo(1.0);
        assertThat(metric.learnedCoverage()).isEqualTo(1.0);
    }

    @Test
    void run_singleDrafts_theFourthChapterIsShownTheLearnedRendering() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book();

        report(TranslationJobTestSupport.job(project, model).run());

        assertLearnedAfterThreeChapters(model, project);
        assertThat(master(project).learned()).isEqualTo(new LexiconEntry.Learned("господар", 4, 4));
    }

    @Test
    void run_stoppedAfterTheFourthTermSegmentThenResumed_leavesNoPhantomCountAndEndsTheSame() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book();
        final TranslationJobImpl first =
                batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED));
        final AtomicInteger decisions = new AtomicInteger();
        final int fourthTermSegment = 7;
        first.subscribe(event -> {
            if (event instanceof SegmentDecided && decisions.incrementAndGet() == fourthTermSegment) {
                first.cancel();
            }
        });

        final JobState stopped = report(first.run()).end();

        assertThat(stopped).isEqualTo(JobState.CANCELLED);
        assertThat(master(project).learned()).isEqualTo(new LexiconEntry.Learned("господар", 3, 3));
        report(batchedJob(project, new DriftingModel(), new RunRequest(project.id(), ReviewMode.UNATTENDED))
                .run());
        assertThat(master(project).learned()).isEqualTo(new LexiconEntry.Learned("господар", 4, 4));
    }
}
