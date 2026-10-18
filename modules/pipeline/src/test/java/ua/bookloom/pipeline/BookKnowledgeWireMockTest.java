package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.setup.BookFacts;
import ua.bookloom.pipeline.setup.BookKnowledge;
import ua.bookloom.pipeline.setup.Knowledge;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * The book-knowledge question at the HTTP seam, in both dialects (15i.B2): the Ollama-native request carries the
 * temperature, the output cap and the schema in its own fields, the OpenAI-compatible one in its own, and a reply or a
 * refusal on the wire becomes the verdict.
 */
class BookKnowledgeWireMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BookFacts FACTS = new BookFacts("The Lantern Ledger", "Orsolya Venn", 2014, null, null);
    private static final String STEM = "o_venn-the_lantern_ledger-2014";
    private static final String KNOWN = """
            {"known":true,"title":"The Lantern Ledger","author":"Orsolya Venn","year":2014,"genre":"fantasy novel",\
            "audience":"adult","era":"late twentieth century","narration":"third person",\
            "summary":"A clerk finds a ledger of unlit lanterns.","confidence":0.9}""";

    private final BookKnowledge knowledge = new BookKnowledge(new PromptTemplates(), MAPPER);
    private final CallFrame frame =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private Knowledge ask(final ChatModel model) {
        final JobModelCalls calls = new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                event -> {},
                Clock.systemUTC(),
                "uk");
        return java.util.Objects.requireNonNull(
                knowledge.ask(FACTS, STEM, frame, calls).data());
    }

    private static JsonNode json(final String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("the request body is not JSON: " + body, cause);
        }
    }

    // IF the temperature were left to the server, THEN the same book could be "known" on one run and not on the next.
    @Test
    void ask_ollamaNative_postsTemperatureZeroNumPredictAndTheSchemaAsFormat() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            provider.stubAlways(provider.reply(KNOWN, Duration.ZERO));

            final Knowledge result = ask(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(provider.chatRequests()).isEqualTo(1);
            final JsonNode body = json(provider.chatBodies().getFirst());
            assertThat(body.path("options").path("temperature").isNumber()).isTrue();
            assertThat(body.path("options").path("temperature").asDouble()).isEqualTo(0.0);
            assertThat(body.path("options").path("num_predict").asInt()).isEqualTo(320);
            assertThat(body.path("format").path("required")).hasSize(10);
            assertThat(body.path("format").path("properties").fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                            "known",
                            "title",
                            "author",
                            "year",
                            "genre",
                            "audience",
                            "era",
                            "narration",
                            "summary",
                            "confidence");
            assertThat(body.path("messages").get(1).path("content").asText()).contains("The Lantern Ledger");
            assertThat(result.isKnown()).isTrue();
            assertThat(Objects.requireNonNull(result.reply()).genre()).isEqualTo("fantasy novel");
        }
    }

    @Test
    void ask_openAiCompatible_postsTemperatureZeroMaxTokensAndTheSchemaAsResponseFormat() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE)) {
            provider.stubAlways(provider.reply(KNOWN, Duration.ZERO));

            final Knowledge result = ask(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(provider.chatRequests()).isEqualTo(1);
            final JsonNode body = json(provider.chatBodies().getFirst());
            assertThat(body.path("temperature").isNumber()).isTrue();
            assertThat(body.path("temperature").asDouble()).isEqualTo(0.0);
            assertThat(body.path("max_tokens").asInt()).isEqualTo(320);
            final JsonNode format = body.path("response_format").path("json_schema");
            assertThat(format.path("name").asText()).isEqualTo("book-knowledge");
            assertThat(format.path("schema").path("required")).hasSize(10);
            assertThat(format.path("schema").path("properties").has("confidence"))
                    .isTrue();
            assertThat(format.path("schema").path("additionalProperties").asBoolean(true))
                    .isFalse();
            assertThat(body.path("messages").get(1).path("content").asText()).contains("Orsolya Venn");
            assertThat(result.isKnown()).isTrue();
        }
    }

    @Test
    void ask_ollamaNativeRefuses_isUnknownCallFailed() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            provider.stubAlways(provider.failure(401));

            final Knowledge result = ask(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(result.isKnown()).isFalse();
            assertThat(result.reason()).isEqualTo(Reason.CALL_FAILED);
        }
    }

    @Test
    void ask_openAiCompatibleRefuses_isUnknownCallFailed() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE)) {
            provider.stubAlways(provider.failure(401));

            final Knowledge result = ask(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(result.isKnown()).isFalse();
            assertThat(result.reason()).isEqualTo(Reason.CALL_FAILED);
        }
    }

    // A model that says "I do not know" on the wire is the same verdict in either dialect.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void ask_modelAnswersNotKnown_isUnknownNotKnownInEitherDialect(final ProviderKind kind) {
        final String notKnown = """
                {"known":false,"title":"","author":"","year":0,"genre":"","audience":"","era":"","narration":"",\
                "summary":"","confidence":0.0}""";
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.reply(notKnown, Duration.ZERO));

            assertThat(ask(provider.model(Duration.ofSeconds(5), duration -> {}))
                            .reason())
                    .isEqualTo(Reason.NOT_KNOWN);
        }
    }
}
