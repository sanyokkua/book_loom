package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.glossary.TermReading;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Subject;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * The term reading at the HTTP seam, in both dialects (15i.N4): the request carries temperature zero, the output cap
 * for its windows and the reading schema — per window the quote before its labels — in each dialect's own fields, and
 * a reply or a refusal on the wire becomes the reading or the error.
 */
class TermReadingWireMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEXT = "Ilsa looked up from the chart. She nodded once.";
    private static final Window WINDOW = new Window(new Mention("Ilsa", 0, 0, 4, 0, true), 0, 0, TEXT, 0, 4);
    private static final Subject ILSA =
            new Subject("Ilsa", 7, new PronounGender.Evidence(List.of("she", "she"), List.of()), List.of());
    private static final String ANSWER = """
            {"windows":[{"window":1,"quote":"She nodded once","usage":"name","kind":"person","gender":"female"}],\
            "speaking":false,"brand":false}""";

    private final TermReading reading = new TermReading(new PromptTemplates(), MAPPER);
    private final CallFrame frame =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private Result<Reading> read(final ChatModel model) {
        final JobModelCalls calls = new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                event -> {},
                Clock.systemUTC(),
                "uk");
        return reading.read(ILSA, List.of(WINDOW), frame, calls);
    }

    private static JsonNode json(final String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("the request body is not JSON: " + body, cause);
        }
    }

    private static List<String> names(final JsonNode node) {
        return MAPPER.convertValue(node, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private static List<String> fieldNames(final JsonNode properties) {
        final List<String> fields = new java.util.ArrayList<>();
        properties.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    // IF the labels came before the quote, THEN a model would label first and pick a quote to fit; the schema makes it
    // copy its evidence first.
    @Test
    void read_ollamaNative_postsTemperatureZeroNumPredictAndTheSchemaWithTheQuoteFirst() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            provider.stubAlways(provider.reply(ANSWER, Duration.ZERO));

            final Result<Reading> result = read(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(provider.chatRequests()).isEqualTo(1);
            final JsonNode body = json(provider.chatBodies().getFirst());
            assertThat(body.path("options").path("temperature").isNumber()).isTrue();
            assertThat(body.path("options").path("temperature").asDouble()).isEqualTo(0.0);
            assertThat(body.path("options").path("num_predict").asInt()).isEqualTo(240);
            final JsonNode item =
                    body.path("format").path("properties").path("windows").path("items");
            assertThat(fieldNames(item.path("properties")))
                    .containsExactly("window", "quote", "usage", "kind", "gender");
            assertThat(names(item.path("required"))).containsExactly("window", "quote", "usage", "kind", "gender");
            assertThat(body.path("messages").get(1).path("content").asText()).contains("[[Ilsa]] looked up");
            assertThat(Objects.requireNonNull(result.data()).windows())
                    .extracting(WindowReading::vote)
                    .containsExactly(Gender.FEMALE);
        }
    }

    @Test
    void read_openAiCompatible_postsTemperatureZeroMaxTokensAndTheSchemaAsResponseFormat() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE)) {
            provider.stubAlways(provider.reply(ANSWER, Duration.ZERO));

            final Result<Reading> result = read(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(provider.chatRequests()).isEqualTo(1);
            final JsonNode body = json(provider.chatBodies().getFirst());
            assertThat(body.path("temperature").asDouble()).isEqualTo(0.0);
            assertThat(body.path("max_tokens").asInt()).isEqualTo(240);
            final JsonNode format = body.path("response_format").path("json_schema");
            assertThat(format.path("name").asText()).isEqualTo("term-reading");
            assertThat(fieldNames(format.path("schema").path("properties")))
                    .containsExactly("windows", "speaking", "brand");
            assertThat(fieldNames(format.path("schema")
                            .path("properties")
                            .path("windows")
                            .path("items")
                            .path("properties")))
                    .containsExactly("window", "quote", "usage", "kind", "gender");
            assertThat(Objects.requireNonNull(result.data()).windows())
                    .extracting(WindowReading::vote)
                    .containsExactly(Gender.FEMALE);
        }
    }

    // A provider that refuses the call is the call's error in either dialect, so the names step can stop and change
    // nothing.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void read_providerRefuses_isTheCallsErrorInEitherDialect(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.failure(401));

            final Result<Reading> result = read(provider.model(Duration.ofSeconds(5), duration -> {}));

            assertThat(result.isErr()).isTrue();
            assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.auth);
        }
    }
}
