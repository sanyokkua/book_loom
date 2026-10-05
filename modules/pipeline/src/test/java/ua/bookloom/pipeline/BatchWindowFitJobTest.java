package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.prompt.PromptBreakdown;

/**
 * A small window is never sent a batch that outgrows it: with long paragraphs, a glossary the items name and previous
 * pairs to show, every batch prompt plus its reply plus the safety margin stays inside the window the run was given.
 */
class BatchWindowFitJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*?)</s>", Pattern.DOTALL);
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";
    private static final String CYRILLIC = "абцдефгхіжклмнопкрстуввхуз";
    private static final int PARAGRAPHS = 24;
    private static final int TERMS = 30;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** Answers a batch with every item written in Cyrillic letters of the same length, so each passes the checks. */
    private static final class Echoing implements ChatModel {

        private final List<ChatRequest> batches = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            final String user = request.messages().get(1).content();
            final Matcher items = ITEM.matcher(user);
            final List<String> entries = new ArrayList<>();
            while (items.find()) {
                entries.add("{\"id\":\"" + items.group(1) + "\",\"target\":\"" + cyrillic(items.group(2)) + "\"}");
            }
            batches.add(request);
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
        return "Hale" + index % TERMS + " walked along the quay of the harbour town while the old men argued about "
                + "the ship nobody had seen, and Moreau" + (index + 1) % TERMS + " counted every barrel that the "
                + "fishermen rolled up from the beach before the tide turned and the lamps were lit in the windows "
                + "of the customs house number " + index + ".";
    }

    private TestProject longBook() {
        final String text = IntStream.range(0, PARAGRAPHS)
                .mapToObj(BatchWindowFitJobTest::paragraph)
                .collect(Collectors.joining("\n\n"));
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), text), brief("en", "uk"));
        IntStream.range(0, TERMS).forEach(index -> {
            addTerm(project, "Hale" + index, "Гейл" + index);
            addTerm(project, "Moreau" + index, "Моро" + index);
        });
        return project;
    }

    private static void addTerm(final TestProject project, final String term, final String target) {
        project.stores()
                .glossary()
                .add(new GlossaryEntry(
                        GlossaryIds.of(project.id(), term),
                        project.id(),
                        term,
                        target,
                        TermType.CHARACTER,
                        Gender.MALE,
                        false));
    }

    @ParameterizedTest
    @ValueSource(ints = {4096, 8192})
    void run_longParagraphsAndRichContext_everyBatchPromptFitsTheWindowWithItsReplyAndMargin(final int window) {
        final Echoing model = new Echoing();
        final TestProject project = longBook();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED, window))
                .run());

        assertThat(model.batches).isNotEmpty();
        assertThat(model.batches.stream().map(BatchWindowFitJobTest::needed).toList())
                .allSatisfy(needed -> assertThat(needed).isLessThanOrEqualTo(window));
    }

    // The prompt as sent, the reply the items are expected to need and the margin the window keeps back.
    private static int needed(final ChatRequest request) {
        final Matcher items = ITEM.matcher(request.messages().get(1).content());
        int source = 0;
        while (items.find()) {
            source += TokenEstimator.estimate(Objects.requireNonNull(items.group(2)), "en");
        }
        final double ratio = TokenEstimator.outputRatio("en", "uk");
        return PromptBreakdown.of(request.messages()).total()
                + (int) Math.ceil(source * ratio)
                + ContextBudget.SAFETY_MARGIN;
    }
}
