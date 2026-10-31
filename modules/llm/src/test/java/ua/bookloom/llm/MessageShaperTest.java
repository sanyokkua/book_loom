package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.ProfiledModels.Dialect;
import ua.bookloom.llm.ProfiledModels.Reply;
import ua.bookloom.llm.client.MessageShaper;
import ua.bookloom.llm.profile.BuiltInRows;
import ua.bookloom.llm.profile.ModelProfiles;

/**
 * Messages are laid out the way each model's template reads them (design D20): folded for a template with no system
 * role, folded after a refusal at no attempt cost, merged once for a template that wants alternating roles, and with a
 * word joiner in a thinking switch for a template that reads one.
 */
class MessageShaperTest {

    private static final String SYSTEM = "You translate prose into Ukrainian.";
    private static final String USER = "Translate: a red door.";
    private static final String SYSTEM_ROLE_REFUSAL =
            "{\"error\":\"Error rendering prompt with jinja template: \\\"System role not supported\\\".\"}";
    private static final String ALTERNATION_REFUSAL =
            "{\"error\":\"Error rendering prompt with jinja template: Jinja Exception: After the optional system"
                    + " message, conversation roles must alternate user/assistant/user/assistant/...\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ProfiledModels models = new ProfiledModels(server);
    private final List<String> heard = new ArrayList<>();
    private final CallAttemptListener listener = new CallAttemptListener() {
        @Override
        public void started(final CallAttempt attempt) {
            heard.add("started " + attempt.number());
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            heard.add("failed " + attempt.number() + " " + code);
        }
    };

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void shape_gemma3_oneUserMessageWithBlankLine() {
        final ModelProfile gemma3 = new ModelProfiles(new BuiltInRows())
                .resolve(
                        ServerEngine.OLLAMA,
                        "gemma3:12b",
                        ModelFacts.unknown(),
                        ModelSettings.defaults("ollama", "gemma3:12b"))
                .profile();

        final ChatRequest shaped = MessageShaper.shaped(gemma3, request(SYSTEM, USER));

        assertThat(shaped.messages()).containsExactly(new ChatMessage(ChatRole.USER, SYSTEM + "\n\n" + USER));
    }

    @Test
    void shape_noSystemMessage_sendsBookLoomsOwn() {
        final ChatRequest shaped = MessageShaper.shaped(
                ModelProfile.defaults(), new ChatRequest(List.of(new ChatMessage(ChatRole.USER, USER))));

        assertThat(shaped.messages())
                .containsExactly(
                        new ChatMessage(ChatRole.SYSTEM, MessageShaper.DEFAULT_SYSTEM),
                        new ChatMessage(ChatRole.USER, USER));
    }

    @Test
    void shape_systemRoleRefusedBy400_resendsFoldedWithoutCostingAnAttempt() {
        models.stub(
                Dialect.LM_STUDIO, new Reply(400, SYSTEM_ROLE_REFUSAL), new Reply(200, Dialect.LM_STUDIO.reply("ok")));
        final GatedChatModel model = models.model(Dialect.LM_STUDIO, "velka-chat-9b");

        final Result<ChatResponse> first = model.chat(request(SYSTEM, USER), listener);
        model.chat(request(SYSTEM, USER));

        assertThat(first.isOk()).isTrue();
        final List<JsonNode> bodies = models.bodies(Dialect.LM_STUDIO);
        assertThat(bodies)
                .extracting(ProfiledModels::roles)
                .containsExactly(List.of("system", "user"), List.of("user"), List.of("user"));
        assertThat(bodies.get(1).path("messages").get(0).path("content").asText())
                .isEqualTo(SYSTEM + "\n\n" + USER);
        assertThat(heard).containsExactly("started 1", "started 1");
        assertThat(models.stored("velka-chat-9b").refused())
                .extracting(RefusedSetting::name, RefusedSetting::insteadDid)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("system_role", "folded"));
    }

    @Test
    void shape_alternatingRolesRefusal_mergesOnceThenTemplateRejected() {
        models.stub(Dialect.LM_STUDIO, new Reply(500, ALTERNATION_REFUSAL));
        final GatedChatModel model = models.model(Dialect.LM_STUDIO, "mistralai/ministral-3-14b-reasoning");

        final AppError error = model.chat(new ChatRequest(List.of(
                        new ChatMessage(ChatRole.SYSTEM, SYSTEM),
                        new ChatMessage(ChatRole.USER, "Glossary: door = двері."),
                        new ChatMessage(ChatRole.USER, USER))))
                .error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(FailureReason.tokenIn(error.details())).isEqualTo("template-rejected");
        assertThat(models.bodies(Dialect.LM_STUDIO))
                .extracting(ProfiledModels::roles)
                .containsExactly(List.of("system", "user", "user"), List.of("system", "user"));
    }

    @Test
    void shape_thinkSlashWord_insertsJoinerAndReplyRemovesIt() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("Введіть /⁠no_think, щоб зупинити.")));
        final GatedChatModel model = models.model(Dialect.OLLAMA, "nemotron-3-nano:4b");

        final ChatResponse response =
                model.chat(request(SYSTEM, "Type /no_think to stop.")).data();

        assertThat(ProfiledModels.texts(models.bodies(Dialect.OLLAMA).getFirst()))
                .contains("Type /⁠no_think to stop.");
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Введіть /no_think, щоб зупинити.");
    }

    private static ChatRequest request(final String system, final String user) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user)),
                0.2,
                null,
                null,
                null,
                null,
                null,
                null,
                CallKind.DRAFT);
    }
}
