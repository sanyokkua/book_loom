package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.chunk.LongParagraphs;

/**
 * A run with the sentence-group arm on (15i.X3) keeps a paragraph of more than 180 words out of every batch: the batch
 * before it ends there, the paragraph is drafted as groups on the single path, and the batch after it starts again.
 */
class LongParagraphBatchJobTest {

    private static final String BATCH = "draft-batch-json";
    private static final String DRAFT = "draft";
    private static final String LONG_PHRASE = "Adored Unknown";
    private static final String S0 = "The old man walked to the harbour.";
    private static final String S1 = "The lighthouse stood on the cliff.";
    private static final String S2 = "The boats rested in the harbour.";
    private static final String S3 = "The wind rose over the dark sea.";
    private static final int FIRST_BATCH_SIZE = 4;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        System.clearProperty(LongParagraphs.PROPERTY_NAME);
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_switchOn_longParagraphIsInNoBatchAndIsDraftedAsGroupsBetweenTwoBatches() throws IOException {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final CyrillicModel model = new CyrillicModel();

        runBook(model);

        final List<String> batches = model.userMessagesOf(BATCH);
        assertThat(batches).hasSize(2);
        assertThat(batches).allSatisfy(user -> assertThat(itemsOf(user)).doesNotContain(LONG_PHRASE));
        assertThat(batches.getFirst()).contains(S0, S1).doesNotContain(S2, S3);
        assertThat(batches.getLast()).contains(S2, S3);
        final List<String> drafts = model.userMessagesOf(DRAFT);
        assertThat(drafts).hasSizeGreaterThan(2);
        assertThat(drafts.subList(0, 3))
                .allSatisfy(
                        user -> assertThat(user).contains("<WholeParagraph>").contains(LONG_PHRASE));
    }

    // Off, the paragraph is a batch item like any other and no prompt knows the block.
    @Test
    void run_switchOff_longParagraphIsABatchItemAndNoPromptHoldsTheBlock() throws IOException {
        final CyrillicModel model = new CyrillicModel();

        runBook(model);

        assertThat(itemsOf(model.userMessagesOf(BATCH).getFirst())).contains(S0, S1, S2, LONG_PHRASE);
        assertThat(model.allUserMessages()).allSatisfy(user -> assertThat(user).doesNotContain("WholeParagraph"));
    }

    // The batch shows the paragraph after it as read-only context, which is not an item; only the items are drafted.
    private static String itemsOf(final String batchPrompt) {
        return batchPrompt.substring(batchPrompt.indexOf("<Items>"));
    }

    private void runBook(final ChatModel model) throws IOException {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", S0, S1, paragraph(), S2, S3)),
                brief("en", "uk"));

        final var run =
                report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), FIRST_BATCH_SIZE)
                        .run());

        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
    }

    private static String paragraph() throws IOException {
        try (InputStream in = LongParagraphBatchJobTest.class.getResourceAsStream("/eval/long-paragraphs.json")) {
            return new ObjectMapper()
                    .readTree(Objects.requireNonNull(in))
                    .get(0)
                    .get("text")
                    .asText();
        }
    }

    /** Answers every item and every draft with the source's Latin letters swapped for Cyrillic ones, tokens kept. */
    private static final class CyrillicModel implements ChatModel {

        private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
        private static final Pattern TEXT = Pattern.compile("<Text>\n(.*)\n</Text>", Pattern.DOTALL);
        private static final Pattern TOKEN = Pattern.compile("⟦g\\d+⟧");
        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String user = userOf(request);
            final String content =
                    switch (formatOf(request)) {
                        case BATCH -> batchReply(user);
                        case "reviewer" -> "{\"results\":[]}";
                        default -> TranslationJobTestSupport.targetReply(cyrillic(bodyOf(user)));
                    };
            return Result.ok(new ChatResponse(content, FinishReason.STOP));
        }

        List<String> userMessagesOf(final String format) {
            return requests.stream()
                    .filter(request -> formatOf(request).equals(format))
                    .map(CyrillicModel::userOf)
                    .toList();
        }

        List<String> allUserMessages() {
            return requests.stream().map(CyrillicModel::userOf).toList();
        }

        private static String batchReply(final String user) {
            final List<String> entries = new ArrayList<>();
            final Matcher matcher = ITEM.matcher(user.substring(user.indexOf("<Items>")));
            while (matcher.find()) {
                entries.add("{\"id\":\"" + matcher.group(1) + "\",\"target\":\""
                        + cyrillic(matcher.group(2)).replace("\"", "\\\"") + "\"}");
            }
            return "{\"items\":[" + String.join(",", entries) + "]}";
        }

        private static String bodyOf(final String user) {
            final Matcher matcher = TEXT.matcher(user);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static String cyrillic(final String text) {
            final Matcher matcher = TOKEN.matcher(text);
            final StringBuilder out = new StringBuilder();
            int from = 0;
            while (matcher.find()) {
                out.append(swap(text.substring(from, matcher.start()))).append(matcher.group());
                from = matcher.end();
            }
            return out.append(swap(text.substring(from))).toString();
        }

        private static String swap(final String text) {
            return text.chars()
                    .map(c -> c >= 'a' && c <= 'z' ? 'а' + (c - 'a') : c >= 'A' && c <= 'Z' ? 'А' + (c - 'A') : c)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }

        private static String formatOf(final ChatRequest request) {
            return Objects.requireNonNull(request.responseFormat(), "format").name();
        }

        private static String userOf(final ChatRequest request) {
            return request.messages().get(1).content();
        }
    }
}
