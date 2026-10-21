package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.persistence.memory.InMemorySettingsRepository;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.ReasoningPlan;
import ua.bookloom.pipeline.prompt.RequestDefaultsSource;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;
import ua.bookloom.pipeline.setup.SetupAssistantImpl;

/**
 * The reasoning level the Generation setting stores reaches the wire of a real call in both dialects (finalize 4.8a):
 * a brief suggestion's two requests carry it as {@code reasoning_effort} on an OpenAI-compatible server and as
 * {@code think} on Ollama, and a set environment plan replaces the stored level as a whole.
 */
class BriefReasoningLevelWireMockTest {

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

    @AfterEach
    void clearProperty() {
        System.clearProperty(ReasoningPlan.PROPERTY_NAME);
    }

    private List<JsonNode> requestsWith(final ProviderKind kind, @Nullable final ReasoningSetting stored) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(
                    List.of(provider.reply(WINDOW, Duration.ZERO), provider.reply(SYNTHESIS, Duration.ZERO)));
            final SettingsRepository settings = new InMemorySettingsRepository();
            if (stored != null) {
                settings.put(AppSetting.REASONING, stored);
            }
            final Desk desk = ReviewFixtures.markdown(tempDir, BOOK);
            final SetupAssistantImpl assistant = new SetupAssistantImpl(
                    desk.projects(),
                    desk.segments(),
                    desk.glossary(),
                    desk.openProjects(),
                    new PromptTemplates(),
                    MAPPER,
                    Clock.systemUTC(),
                    new RequestDefaultsSource(settings));
            assertThat(assistant
                            .suggestBrief(desk.projectId(), provider.model(Duration.ofSeconds(5), duration -> {}))
                            .error())
                    .isNull();
            return provider.chatBodies().stream()
                    .map(BriefReasoningLevelWireMockTest::json)
                    .toList();
        }
    }

    private static JsonNode json(final String body) {
        try {
            return MAPPER.readTree(body);
        } catch (java.io.IOException cause) {
            throw new AssertionError("the request body is not JSON: " + body, cause);
        }
    }

    // IF Low is stored and nothing overrides it, THEN every call of the run asks the server for low effort.
    @ParameterizedTest
    @CsvSource({"OPENAI_COMPATIBLE,/reasoning_effort,low", "OLLAMA,/think,low"})
    void suggestBrief_storedLow_everyRequestCarriesLow(
            final ProviderKind kind, final String pointer, final String low) {
        final List<JsonNode> bodies = requestsWith(kind, ReasoningSetting.LOW);

        assertThat(bodies).hasSize(2);
        assertThat(bodies)
                .allSatisfy(body -> assertThat(body.at(pointer).asText()).isEqualTo(low));
    }

    // IF the stored level is On, THEN the request asks for the middle effort on OpenAI and true on Ollama.
    @Test
    void suggestBrief_storedOnOpenAi_sendsMedium() {
        assertThat(requestsWith(ProviderKind.OPENAI_COMPATIBLE, ReasoningSetting.ON))
                .allSatisfy(body ->
                        assertThat(body.path("reasoning_effort").asText()).isEqualTo("medium"));
    }

    @Test
    void suggestBrief_storedOnOllama_sendsThinkTrue() {
        assertThat(requestsWith(ProviderKind.OLLAMA, ReasoningSetting.ON))
                .allSatisfy(
                        body -> assertThat(body.path("think").asBoolean(false)).isTrue());
    }

    // IF nothing is stored, THEN calls carry Off as they always did: none on OpenAI, think false on Ollama.
    @Test
    void suggestBrief_nothingStoredOpenAi_sendsNone() {
        assertThat(requestsWith(ProviderKind.OPENAI_COMPATIBLE, null))
                .allSatisfy(body ->
                        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none"));
    }

    @Test
    void suggestBrief_nothingStoredOllama_sendsThinkFalse() {
        assertThat(requestsWith(ProviderKind.OLLAMA, null)).allSatisfy(body -> {
            assertThat(body.has("think")).isTrue();
            assertThat(body.path("think").asBoolean(true)).isFalse();
        });
    }

    // IF the property names a level, THEN it replaces the stored one as a whole.
    @Test
    void suggestBrief_propertyOnWithStoredOff_sendsMedium() {
        System.setProperty(ReasoningPlan.PROPERTY_NAME, "on");

        assertThat(requestsWith(ProviderKind.OPENAI_COMPATIBLE, ReasoningSetting.OFF))
                .allSatisfy(body ->
                        assertThat(body.path("reasoning_effort").asText()).isEqualTo("medium"));
    }
}
