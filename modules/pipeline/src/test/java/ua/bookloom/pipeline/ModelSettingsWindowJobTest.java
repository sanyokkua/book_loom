package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A context window the person saves while a run goes applies from the next chunk: the chunk running keeps the window
 * it was packed for, and the segments not yet packed are packed again for the new one. Sixteen paragraphs in one
 * unit on the Fast dial are two chunks of eight.
 */
class ModelSettingsWindowJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*?)</s>", Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile("number (\\d+)\\.");
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";
    private static final String CYRILLIC = "абцдефгхіжклмнопкрстуввхуз";
    private static final int PARAGRAPHS = 16;
    private static final int FIRST_CHUNK_SIZE = 8;
    private static final int DEFAULT_WINDOW = 16384;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** One batch call as the model saw it: the window it asked for and the paragraph numbers it carried. */
    private record Seen(@Nullable Integer window, List<Integer> paragraphs) {}

    /** Echoes each batch in Cyrillic letters; the window the person saves is set when the first call arrives. */
    private static final class Echoing implements ChatModel {

        private final List<Seen> calls = new CopyOnWriteArrayList<>();
        private final AtomicReference<@Nullable Integer> stored = new AtomicReference<>();
        private final int savedOnFirstCall;

        Echoing(final int savedOnFirstCall) {
            this.savedOnFirstCall = savedOnFirstCall;
        }

        @Override
        public @Nullable Integer contextWindowOverride() {
            return stored.get();
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            final String user = request.messages().get(1).content();
            final Matcher items = ITEM.matcher(user);
            final List<String> entries = new ArrayList<>();
            final List<Integer> numbers = new ArrayList<>();
            while (items.find()) {
                entries.add("{\"id\":\"" + items.group(1) + "\",\"target\":\"" + cyrillic(items.group(2)) + "\"}");
                final Matcher number = NUMBER.matcher(items.group(2));
                number.find();
                numbers.add(Integer.parseInt(number.group(1)));
            }
            calls.add(new Seen(request.contextWindow(), numbers));
            stored.compareAndSet(null, savedOnFirstCall);
            return Result.ok(new ChatResponse("{\"items\":[" + String.join(",", entries) + "]}", FinishReason.STOP));
        }

        private static String cyrillic(final String source) {
            final StringBuilder out = new StringBuilder();
            source.chars()
                    .forEach(c -> out.append(
                            LATIN.indexOf(Character.toLowerCase(c)) < 0
                                    ? (char) c
                                    : CYRILLIC.charAt(LATIN.indexOf(Character.toLowerCase(c)))));
            return out.toString();
        }
    }

    private static String paragraph(final int index) {
        final String sentence = "Hale walked along the quay of the harbour town while the old men argued about the "
                + "ship nobody had seen and the fishermen rolled barrels up from the beach before the tide turned";
        return sentence + " and " + sentence + ", at the customs house number " + index + ".";
    }

    private TestProject book() {
        final String text = IntStream.range(0, PARAGRAPHS)
                .mapToObj(ModelSettingsWindowJobTest::paragraph)
                .collect(Collectors.joining("\n\n"));
        return project(TestBooks.markdown(tempDir.resolve("Book.md"), text), brief("en", "uk"));
    }

    private static boolean carries(final Seen seen, final int paragraph) {
        return seen.paragraphs().contains(paragraph);
    }

    // IF the window is saved during chunk 1, THEN chunk 1 finishes at the window it was packed for and chunk 2 starts
    // at the new one.
    @Test
    void run_contextSavedDuringChunk1_reachesChunk2NotRestOfChunk1() {
        final Echoing model = new Echoing(8192);
        final TestProject project = book();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED))
                .run());

        final List<Seen> chunkOne = model.calls.stream()
                .filter(seen -> seen.paragraphs().stream().anyMatch(number -> number < FIRST_CHUNK_SIZE))
                .toList();
        final Seen firstOfChunkTwo = model.calls.stream()
                .filter(seen -> carries(seen, FIRST_CHUNK_SIZE))
                .findFirst()
                .orElseThrow();
        assertThat(chunkOne)
                .isNotEmpty()
                .allSatisfy(seen -> assertThat(seen.window()).isEqualTo(DEFAULT_WINDOW));
        assertThat(firstOfChunkTwo.window()).isEqualTo(8192);
    }

    // IF the new window is too small for the eight paragraphs waiting, THEN they are packed into smaller chunks.
    @Test
    void run_windowShrinksMidUnit_remainingSegmentsRepacked() {
        final Echoing model = new Echoing(4096);
        final TestProject project = book();

        final var run = report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED))
                .run());

        final List<Seen> later = model.calls.stream()
                .filter(seen -> seen.paragraphs().stream().allMatch(number -> number >= FIRST_CHUNK_SIZE))
                .toList();
        assertThat(model.calls.getFirst().paragraphs()).hasSize(FIRST_CHUNK_SIZE);
        assertThat(later)
                .isNotEmpty()
                .allSatisfy(seen -> assertThat(seen.paragraphs()).hasSizeLessThan(FIRST_CHUNK_SIZE));
        assertThat(run.end()).isEqualTo(ua.bookloom.api.pipeline.JobState.COMPLETED);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, PARAGRAPHS, 0, 0, 0));
    }
}
