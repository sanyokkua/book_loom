package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.SamplingParams;

/** Sampling is the server's own unless a mode asks for the tuned profile; an unknown name never fails a run. */
class SamplingModeTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));

    @AfterEach
    void clearProperty() {
        System.clearProperty(SamplingMode.PROPERTY_NAME);
    }

    @ParameterizedTest
    @CsvSource({
        "server,,SERVER",
        "tuned,,TUNED",
        " TUNED ,,TUNED",
        ",tuned,TUNED",
        "server,tuned,SERVER",
        "bogus,,SERVER",
        ",,SERVER"
    })
    void resolve_envThenProperty_namesTheMode(String env, String property, SamplingMode expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(SamplingMode.ENV_NAME, env);

        assertThat(SamplingMode.resolve(environment, property)).isEqualTo(expected);
    }

    @Test
    void default_isServer_untilTheTunedProfileIsMeasured() {
        assertThat(SamplingMode.DEFAULT).isEqualTo(SamplingMode.SERVER);
    }

    @Test
    void build_tunedProperty_sendsTheDraftProfile() {
        System.setProperty(SamplingMode.PROPERTY_NAME, "tuned");

        final ChatRequest request = ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false);

        assertThat(request.sampling()).isEqualTo(new SamplingParams(0.9, 40, 0.05, 1.0));
    }

    @Test
    void build_tunedProperty_sendsTheNarrowerAnalysisProfileToTheReviewer() {
        System.setProperty(SamplingMode.PROPERTY_NAME, "tuned");

        final ChatRequest request = ChatRequests.build(PromptName.REVIEWER, MESSAGES, null, false);

        assertThat(request.sampling()).isEqualTo(new SamplingParams(0.9, 20, 0.05, 1.0));
    }

    @Test
    void build_serverProperty_sendsNoSampling() {
        System.setProperty(SamplingMode.PROPERTY_NAME, "server");

        assertThat(ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false).sampling())
                .isNull();
    }
}
