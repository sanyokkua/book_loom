package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.ProfiledModels.Dialect;
import ua.bookloom.llm.ProfiledModels.Reply;

/**
 * Structured answers step down one rung per model and call kind (design D20): a schema one kind cannot use moves that
 * kind alone to the prompt-only rung, a server with no structured output moves every kind, a row that starts low sends
 * no schema, and a call that thinks never carries one.
 */
class StructuredRungsTest {

    private static final String GEMMA = "google/gemma-4-e4b";
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"}}}";
    private static final ResponseFormat DRAFT = new ResponseFormat("draft", SCHEMA);
    private static final ResponseFormat SUMMARY = new ResponseFormat("summary", SCHEMA);
    private static final String GRAMMAR_REFUSAL =
            "{\"error\":\"Failed to initialize samplers: failed to parse grammar\"}";
    private static final String NO_STRUCTURED = "{\"error\":\"structured output is unavailable\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ProfiledModels models = new ProfiledModels(server);

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void step_oneCallKindFails_onlyThatKindMovesDown() {
        models.stub(Dialect.LM_STUDIO, new Reply(400, GRAMMAR_REFUSAL), ok());
        final GatedChatModel model = models.model(Dialect.LM_STUDIO, GEMMA);

        model.chat(request(DRAFT, CallKind.DRAFT));
        model.chat(request(DRAFT, CallKind.DRAFT));
        model.chat(request(SUMMARY, CallKind.SUMMARY));

        assertThat(models.bodies(Dialect.LM_STUDIO))
                .extracting(body -> body.has("response_format"))
                .containsExactly(true, false, false, true);
        assertThat(models.stored(GEMMA).refused())
                .extracting(RefusedSetting::name, RefusedSetting::scope)
                .containsExactly(tuple("response_format", "draft"));
    }

    @Test
    void step_serverHasNoStructuredOutput_everyKindMovesDown() {
        models.stub(Dialect.LM_STUDIO, new Reply(501, NO_STRUCTURED), ok());
        final GatedChatModel model = models.model(Dialect.LM_STUDIO, GEMMA);

        model.chat(request(DRAFT, CallKind.DRAFT));
        model.chat(request(SUMMARY, CallKind.SUMMARY));

        assertThat(models.bodies(Dialect.LM_STUDIO))
                .extracting(body -> body.has("response_format"))
                .containsExactly(true, false, false);
        assertThat(models.stored(GEMMA).refused())
                .extracting(RefusedSetting::name, RefusedSetting::scope)
                .containsExactly(tuple("response_format", null));
    }

    @Test
    void step_rowStartsOnThePromptOnlyRung_sendsNoSchema() {
        models.stub(Dialect.LM_STUDIO, ok());

        models.model(Dialect.LM_STUDIO, "lapa-v0.1.2-instruct").chat(request(DRAFT, CallKind.DRAFT));

        assertThat(models.bodies(Dialect.LM_STUDIO).getFirst().has("response_format"))
                .isFalse();
    }

    @Test
    void step_callThatThinks_sendsNoSchema() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("{\"target\":\"двері\"}")));

        models.model(Dialect.OLLAMA, "gpt-oss:20b").chat(request(DRAFT, CallKind.DRAFT));

        final JsonNode body = models.bodies(Dialect.OLLAMA).getFirst();
        assertThat(body.path("think").asText()).isEqualTo("low");
        assertThat(body.has("format")).isFalse();
    }

    @Test
    void step_sendAgainOnTheScopedRow_sendsThatSchemaAgain() {
        models.stub(Dialect.LM_STUDIO, new Reply(400, GRAMMAR_REFUSAL), ok());
        final GatedChatModel model = models.model(Dialect.LM_STUDIO, GEMMA);
        model.chat(request(DRAFT, CallKind.DRAFT));

        models.repository()
                .update(ProfiledModels.PROVIDER, GEMMA, stored -> stored.withoutRefusal("response_format", "draft"));
        model.chat(request(DRAFT, CallKind.DRAFT));

        assertThat(models.bodies(Dialect.LM_STUDIO).getLast().has("response_format"))
                .isTrue();
    }

    private static Reply ok() {
        return new Reply(200, Dialect.LM_STUDIO.reply("{\"target\":\"двері\"}"));
    }

    private static ChatRequest request(final ResponseFormat format, final CallKind kind) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, "Translate."), new ChatMessage(ChatRole.USER, "a door")),
                0.2,
                format,
                ReasoningLevel.OFF,
                null,
                null,
                null,
                null,
                kind);
    }
}
