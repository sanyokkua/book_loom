package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.judge.JudgeCall;
import ua.bookloom.pipeline.judge.JudgeReplyParser;
import ua.bookloom.pipeline.judge.JudgedPair;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** A draft then a judge call each reach the wire at their own temperature, reasoning off, and their own schema. */
class JudgeTemperatureWireMockTest {

    private static final JudgeCall JUDGE_CALL =
            new JudgeCall(new PromptTemplates(), new JudgeReplyParser(new ObjectMapper()));
    private static final String SOURCE_TEXT = "He opened the old door.";
    private static final String DRAFT_TARGET = "Він відчинив старі двері.";
    private static final String JUDGE_REPLY = "{\"score\":0.9,\"verdict\":\"accept\",\"findings\":[],\"deferrals\":[]}";
    private static final String THINK_OFF = "\"think\":false";

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_draftThenJudge_eachPostsItsOwnTemperatureReasoningAndSchema(final ProviderKind kind) {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Segment segment = firstSegment(documents);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(
                    List.of(provider.target(DRAFT_TARGET, Duration.ZERO), provider.reply(JUDGE_REPLY, Duration.ZERO)));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});
            TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en")
                    .translate(segment);
            JUDGE_CALL.judge(
                    List.of(new JudgedPair(segment.id(), segment.masked(), DRAFT_TARGET)),
                    new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP),
                    List.of(),
                    (callKind, segmentId, request) -> model.chat(request));

            final List<String> bodies = provider.chatBodies();
            assertThat(bodies.get(0))
                    .contains("\"temperature\":0.2", "\"target\"")
                    .doesNotContain("\"score\"");
            assertThat(bodies.get(1))
                    .contains("\"temperature\":0.1", "\"score\"")
                    .doesNotContain("\"target\"");
            assertReasoningOff(kind, bodies);
        }
    }

    private Segment firstSegment(final DocumentPort documents) {
        return Objects.requireNonNull(documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), SOURCE_TEXT))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }

    private static void assertReasoningOff(final ProviderKind kind, final List<String> bodies) {
        switch (kind) {
            case OLLAMA ->
                assertThat(bodies).allSatisfy(body -> assertThat(body).contains(THINK_OFF));
            case OPENAI_COMPATIBLE ->
                assertThat(bodies).allSatisfy(body -> assertThat(body).doesNotContain("\"think\"", "\"reasoning\""));
        }
    }
}
