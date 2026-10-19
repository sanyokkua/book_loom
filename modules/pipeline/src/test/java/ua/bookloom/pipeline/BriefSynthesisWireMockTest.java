package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;
import ua.bookloom.pipeline.setup.SetupAssistantImpl;

/**
 * The brief's synthesis call at the HTTP seam, in both dialects (15i.B4): after the one window of a short untitled
 * book, the second request carries temperature zero, the 320-token cap and the synthesis schema in the dialect's own
 * fields, with each field's basis before its value; its reply becomes the suggested genre.
 */
class BriefSynthesisWireMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUOTE = "The rain had not stopped for three days";
    private static final String BOOK = QUOTE + ", and the lamps along the quay burned all afternoon.\n\n"
            + "The ferryman counted his fares twice and closed the shed before the tide turned.\n\n"
            + "Nobody came down the lane that evening, and the gulls kept to the roofs.\n";
    private static final String WINDOW = "{\"genre\":{\"evidence\":\"" + QUOTE
            + "\",\"value\":\"quiet harbour mystery\",\"confidence\":0.9},"
            + "\"register\":{\"evidence\":\"\",\"value\":\"neutral\",\"confidence\":0.5},"
            + "\"voice\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"audience\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"narrator\":{\"evidence\":\"\",\"value\":\"unspecified\",\"confidence\":0.5},"
            + "\"narratorGender\":{\"evidence\":\"\",\"value\":\"unknown\",\"name\":\"\",\"confidence\":0.5}}";
    private static final String SYNTHESIS = "{\"genre\":{\"basis\":{\"knowledge\":false,\"passages\":[1]},"
            + "\"value\":\"Mystery\"},\"audience\":{\"basis\":{\"knowledge\":false,\"passages\":[]},\"value\":\"\"},"
            + "\"voice\":{\"basis\":{\"knowledge\":false,\"passages\":[]},\"value\":\"\"}}";

    @TempDir
    private Path tempDir;

    private BriefSuggestion suggest(final WireMockProvider provider) {
        provider.stubSequence(List.of(provider.reply(WINDOW, Duration.ZERO), provider.reply(SYNTHESIS, Duration.ZERO)));
        final Desk desk = ReviewFixtures.markdown(tempDir, BOOK);
        final SetupAssistantImpl assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                MAPPER,
                Clock.systemUTC());
        final Result<BriefSuggestion> result =
                assistant.suggestBrief(desk.projectId(), provider.model(Duration.ofSeconds(5), duration -> {}));
        assertThat(result.error()).isNull();
        return java.util.Objects.requireNonNull(result.data(), "suggestion");
    }

    private static JsonNode json(final String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("the request body is not JSON: " + body, cause);
        }
    }

    private static List<String> names(final JsonNode object) {
        final List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // IF the temperature were left to the server, THEN the same readings could give a different genre on each run.
    @Test
    void suggestBrief_ollamaNative_postsTheSynthesisWithTemperatureZeroNumPredictAndItsSchemaAsFormat() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            final BriefSuggestion suggestion = suggest(provider);

            assertThat(provider.chatRequests()).isEqualTo(2);
            final JsonNode body = json(provider.chatBodies().get(1));
            assertThat(body.path("options").path("temperature").asDouble(-1)).isEqualTo(0.0);
            assertThat(body.path("options").path("num_predict").asInt()).isEqualTo(320);
            assertThat(names(body.path("format").path("properties"))).containsExactly("genre", "audience", "voice");
            assertThat(names(
                            body.path("format").path("properties").path("genre").path("properties")))
                    .containsExactly("basis", "value");
            assertThat(body.path("messages").get(1).path("content").asText()).contains("quiet harbour mystery");
            assertThat(suggestion.genre()).isEqualTo("Mystery");
        }
    }

    @Test
    void suggestBrief_openAiCompatible_postsTheSynthesisWithTemperatureZeroMaxTokensAndItsSchema() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE)) {
            final BriefSuggestion suggestion = suggest(provider);

            assertThat(provider.chatRequests()).isEqualTo(2);
            final JsonNode body = json(provider.chatBodies().get(1));
            assertThat(body.path("temperature").asDouble(-1)).isEqualTo(0.0);
            assertThat(body.path("max_tokens").asInt()).isEqualTo(320);
            final JsonNode format = body.path("response_format").path("json_schema");
            assertThat(format.path("name").asText()).isEqualTo("brief-synthesis");
            assertThat(names(format.path("schema")
                            .path("properties")
                            .path("voice")
                            .path("properties")))
                    .containsExactly("basis", "value");
            assertThat(body.path("messages").get(1).path("content").asText()).contains(QUOTE);
            assertThat(suggestion.genre()).isEqualTo("Mystery");
        }
    }
}
