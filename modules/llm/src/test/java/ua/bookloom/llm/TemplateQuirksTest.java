package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.ProfiledModels.Dialect;
import ua.bookloom.llm.ProfiledModels.Reply;

/**
 * The template quirks the built-in rows set are honoured on the wire, on both dialects (design D20): a template that
 * writes the reasoning level itself sees no level line of BookLoom's, and a harmony-format model reads its level only
 * from the engine's field, and a schema that cuts a translation at a quote mark moves that call kind to the prompt-only
 * rung.
 */
class TemplateQuirksTest {

    private static final String GLIMMER = "muse-glimmer:30b-nvfp4-dflash";
    private static final String GPT_OSS = "gpt-oss:20b";
    private static final String USER = "Translate: a red door.";
    private static final String GEMMA3 = "gemma3:12b";
    private static final String WHOLE = "{\"target\":\"Він сказав «так» і пішов.\"}";

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

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void chat_museGlimmer_systemTextNamesNoReasoningLevel(final Dialect dialect) {
        models.stub(dialect, new Reply(200, dialect.reply("червоні двері")));

        models.model(dialect, GLIMMER)
                .chat(request(
                        "You translate prose.\nReasoning strength: high.\nReasoning effort: medium\nKeep names."));

        final String sent = ProfiledModels.texts(models.bodies(dialect).getFirst());
        assertThat(sent)
                .doesNotContainIgnoringCase("Reasoning strength")
                .doesNotContainIgnoringCase("Reasoning effort");
        assertThat(sent).contains("You translate prose.\nKeep names.");
    }

    @Test
    void chat_museGlimmerOnOllama_sendsLowThroughTheField() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("червоні двері")));

        models.model(Dialect.OLLAMA, GLIMMER).chat(request("You translate prose."));

        assertThat(models.bodies(Dialect.OLLAMA).getFirst().path("think").asText())
                .isEqualTo("low");
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void chat_gptOss_noMessageHoldsAReasoningLine(final Dialect dialect) {
        models.stub(dialect, new Reply(200, dialect.reply("червоні двері")));

        models.model(dialect, GPT_OSS)
                .chat(new ChatRequest(
                        List.of(
                                new ChatMessage(ChatRole.SYSTEM, "You translate prose.\nReasoning: high"),
                                new ChatMessage(ChatRole.USER, "Reasoning: medium\n" + USER)),
                        0.2,
                        null,
                        ReasoningLevel.OFF,
                        null,
                        null,
                        null,
                        null,
                        CallKind.DRAFT));

        assertThat(ProfiledModels.texts(models.bodies(dialect).getFirst()))
                .doesNotContainPattern("(?m)^\\s*Reasoning:")
                .contains(USER);
    }

    @Test
    void chat_gptOssOnOllama_sendsLowThroughTheField() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("червоні двері")));

        models.model(Dialect.OLLAMA, GPT_OSS).chat(request("You translate prose."));

        assertThat(models.bodies(Dialect.OLLAMA).getFirst().path("think").asText())
                .isEqualTo("low");
    }

    @Test
    void chat_gemma3QuoteCutTarget_resendsOnPromptOnlyRungOnce() {
        models.stub(
                Dialect.OLLAMA,
                new Reply(200, Dialect.OLLAMA.reply("{\"target\":\"Він сказав \"}")),
                new Reply(200, Dialect.OLLAMA.reply(WHOLE)));
        final GatedChatModel model = models.model(Dialect.OLLAMA, GEMMA3);

        final ChatResponse first = model.chat(quoted()).data();
        model.chat(quoted());

        assertThat(first).isNotNull();
        assertThat(first.content()).isEqualTo(WHOLE);
        assertThat(models.bodies(Dialect.OLLAMA))
                .extracting(body -> body.has("format"))
                .containsExactly(true, false, false);
        assertThat(models.stored(GEMMA3).refused())
                .extracting(RefusedSetting::name, RefusedSetting::scope)
                .containsExactly(tuple("response_format", "draft"));
    }

    @Test
    void chat_gemma3WholeTarget_staysOnTheSchemaRung() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply(WHOLE)));
        final GatedChatModel model = models.model(Dialect.OLLAMA, GEMMA3);

        model.chat(quoted());
        model.chat(quoted());

        assertThat(models.bodies(Dialect.OLLAMA))
                .extracting(body -> body.has("format"))
                .containsExactly(true, true);
        assertThat(models.stored(GEMMA3).refused()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Він сказав:", "Але я—"})
    void chat_gemma3OpenEndedTargetOfAnUnquotedSource_staysOnTheSchemaRung(final String target) {
        models.stub(
                Dialect.OLLAMA,
                new Reply(200, Dialect.OLLAMA.reply("{\"items\":[{\"id\":\"b\",\"target\":\"" + target + "\"}]}")));
        final GatedChatModel model = models.model(Dialect.OLLAMA, GEMMA3);

        model.chat(batch(target));
        model.chat(batch(target));

        assertThat(models.bodies(Dialect.OLLAMA))
                .extracting(body -> body.has("format"))
                .containsExactly(true, true);
        assertThat(models.stored(GEMMA3).refused()).isEmpty();
    }

    // Item a opens a quotation, item b does not; the schema line and the folded system text hold straight quotes too.
    private static ChatRequest batch(final String source) {
        return new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "You translate prose."),
                        new ChatMessage(
                                ChatRole.USER,
                                "<Items>\n<s id=\"a\">Він сказав \"так\" і пішов.</s>\n<s id=\"b\">" + source
                                        + "</s>\n</Items>\nReturn exactly one JSON object matching this schema:"
                                        + " {\"items\":[{\"id\":\"<id>\",\"target\":\"<translation>\"}]}")),
                0.2,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                ReasoningLevel.OFF,
                null,
                null,
                null,
                null,
                CallKind.DRAFT);
    }

    private static ChatRequest quoted() {
        return new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "You translate prose."),
                        new ChatMessage(ChatRole.USER, "<s id=\"ch02.xhtml:7\">Він сказав \"так\" і пішов.</s>")),
                0.2,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                ReasoningLevel.OFF,
                null,
                null,
                null,
                null,
                CallKind.DRAFT);
    }

    private static ChatRequest request(final String system) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, USER)),
                0.2,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                null,
                null,
                CallKind.DRAFT);
    }
}
