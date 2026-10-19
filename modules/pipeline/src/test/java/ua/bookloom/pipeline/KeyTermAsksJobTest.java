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
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.batch.KeyTermAsks;

/** A model that never reports a key-term rendering is asked for it in the first batches only (15h.C4). */
class KeyTermAsksJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final int BATCH_SIZE = 2;
    private static final List<String> ADJECTIVES = List.of(
            "old", "young", "tall", "quiet", "angry", "proud", "weary", "kind", "cold", "bold", "shy", "wise", "calm",
            "stern", "gentle", "brave");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** Translates every item to the same sentence and never adds a "terms" field. */
    private static final class DeafModel implements ChatModel {

        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final List<String> entries = new ArrayList<>();
            final Matcher items = ITEM.matcher(userMessage(request));
            while (items.find()) {
                entries.add("{\"id\":\"" + items.group(1) + "\",\"target\":\"Господар пішов до гавані.\"}");
            }
            return Result.ok(new ChatResponse("{\"items\":[" + String.join(",", entries) + "]}", FinishReason.STOP));
        }
    }

    @Test
    void run_modelNeverReportsTerms_stopsAskingAfterTheProbeBatches() {
        final DeafModel model = new DeafModel();
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"),
                        String.join(
                                "\n\n",
                                ADJECTIVES.stream()
                                        .map(adjective -> "The " + adjective + " master walked to the harbour.")
                                        .toList())),
                brief("en", "uk"));
        project.stores().lexicon().put(LexiconEntry.of(project.id(), "master"));

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), BATCH_SIZE)
                .run());

        final List<Boolean> asked = model.requests.stream()
                .filter(request -> userMessage(request).contains("<s id=\"1\">"))
                .map(request -> userMessage(request).contains("[Key terms"))
                .toList();
        assertThat(asked).hasSizeGreaterThan(KeyTermAsks.PROBE_BATCHES);
        assertThat(asked.subList(0, KeyTermAsks.PROBE_BATCHES)).containsOnly(true);
        assertThat(asked.subList(KeyTermAsks.PROBE_BATCHES, asked.size())).containsOnly(false);
    }
}
