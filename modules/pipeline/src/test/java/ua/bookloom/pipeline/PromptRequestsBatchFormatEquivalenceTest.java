package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.eval.EvalContext;
import ua.bookloom.pipeline.eval.EvalProject;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * What the prompt evals send is what a running job sends, in each arm of the batch format (15i.X1): the eval path
 * reads the same {@code bookloom.batch.format} property as the job, so a report made with
 * {@code BOOKLOOM_EVAL_BATCH_FORMAT=markers} measured the marked passage and not the JSON items.
 */
class PromptRequestsBatchFormatEquivalenceTest {

    private static final String PROPERTY = "bookloom.batch.format";
    private static final List<String> SOURCES = List.of(
            "The old man walked to the harbour.",
            "The lighthouse stood on the cliff.",
            "The boats rested in the harbour.",
            "The wind rose over the dark sea.");
    private static final List<String> TARGETS = List.of(
            "Старий чоловік пішов до гавані.",
            "Маяк стояв на прибережній скелі.",
            "Човни спокійно стояли в гавані.",
            "Вітер здійнявся над темним морем.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        System.clearProperty(PROPERTY);
        TranslationJobTestSupport.shutdownAll();
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private static String json() {
        final StringBuilder entries = new StringBuilder();
        for (int i = 0; i < TARGETS.size(); i++) {
            entries.append(i == 0 ? "" : ",")
                    .append("{\"id\":\"")
                    .append(i + 1)
                    .append("\",\"target\":\"")
                    .append(TARGETS.get(i))
                    .append("\"}");
        }
        return "{\"items\":[" + entries + "]}";
    }

    private static String marked() {
        final StringBuilder reply = new StringBuilder();
        for (int i = 0; i < TARGETS.size(); i++) {
            reply.append(i == 0 ? "" : "\n\n")
                    .append("⟦s")
                    .append(i + 1)
                    .append("⟧ ")
                    .append(TARGETS.get(i));
        }
        return reply.toString();
    }

    private ChatRequest jobsBatchRequest(final ScriptedChatModel model) {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", SOURCES), "en"),
                brief("en", "uk", QualityDial.BALANCED));

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 4)
                .run());
        return model.requests().getFirst();
    }

    private static ChatRequest evalsBatchRequest() {
        final ScriptedChatModel recording = new ScriptedChatModel().answer(reply("{}"));
        final JobModelCalls calls =
                new JobModelCalls(onSent -> recording, event -> {}, Clock.systemUTC(), "uk", EvalProject.window());
        final EvalProject eval = EvalProject.of(
                new EvalProject.Setup("en", "uk", "en", List.of(), EvalContext.none(), SOURCES, List.of()), calls);
        return eval.batchRequest(eval.batch(0, 4).orElseThrow());
    }

    @Test
    void batchRequest_markersFormat_equalsTheRequestTheJobSendsAndCarriesNoSchema() {
        System.setProperty(PROPERTY, "markers");
        final ChatRequest job = jobsBatchRequest(
                new ScriptedChatModel().answer(reply(marked())).answerTo("reviewer", reply("{\"results\":[]}")));

        final ChatRequest eval = evalsBatchRequest();

        assertThat(job.responseFormat()).isNull();
        assertThat(job.messages().get(1).content()).contains("⟦s1⟧ " + SOURCES.get(0));
        assertThat(eval).isEqualTo(job);
    }

    @Test
    void batchRequest_dualFormat_equalsTheRequestTheJobSendsAndKeepsTheSchema() {
        System.setProperty(PROPERTY, "dual");
        final ChatRequest job = jobsBatchRequest(new ScriptedChatModel()
                .answerTo("draft-batch-json", reply(json()))
                .answerTo("reviewer", reply("{\"results\":[]}")));

        final ChatRequest eval = evalsBatchRequest();

        final String user = job.messages().get(1).content();
        assertThat(job.responseFormat()).isNotNull();
        assertThat(user).containsSubsequence(SOURCES.get(0), SOURCES.get(1), "<Items>", "<s id=\"1\">");
        assertThat(user.split(java.util.regex.Pattern.quote(SOURCES.get(1)), -1))
                .hasSize(3);
        assertThat(eval).isEqualTo(job);
    }

    @Test
    void batchRequest_noFormatSet_isTheJsonRequestOnBothPaths() {
        final ChatRequest job = jobsBatchRequest(new ScriptedChatModel()
                .answerTo("draft-batch-json", reply(json()))
                .answerTo("reviewer", reply("{\"results\":[]}")));

        final ChatRequest eval = evalsBatchRequest();

        assertThat(job.messages().get(1).content()).doesNotContain("⟦s1⟧");
        assertThat(eval).isEqualTo(job);
    }
}
