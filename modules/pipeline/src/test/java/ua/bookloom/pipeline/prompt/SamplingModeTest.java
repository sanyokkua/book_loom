package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingParams;

/** Sampling is the server's own unless a mode asks for the tuned profile; an unknown name never fails a run. */
class SamplingModeTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));

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
        final ChatRequest request =
                ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false, defaults(SamplingMode.TUNED));

        assertThat(request.sampling()).isEqualTo(new SamplingParams(0.9, 40, 0.05, 1.0));
    }

    @Test
    void build_tunedProperty_sendsTheNarrowerAnalysisProfileToTheReviewer() {
        final ChatRequest request =
                ChatRequests.build(PromptName.REVIEWER, MESSAGES, null, false, defaults(SamplingMode.TUNED));

        assertThat(request.sampling()).isEqualTo(new SamplingParams(0.9, 20, 0.05, 1.0));
    }

    @Test
    void build_serverProperty_sendsNoSampling() {
        assertThat(ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false, defaults(SamplingMode.SERVER))
                        .sampling())
                .isNull();
    }

    // The environment variable or property overrides the stored setting only when it names a known mode.
    @ParameterizedTest
    @CsvSource({"tuned,,TUNED", " SERVER ,,SERVER", ",tuned,TUNED", "server,tuned,SERVER"})
    void explicit_knownName_isPresent(String env, String property, SamplingMode expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(SamplingMode.ENV_NAME, env);

        assertThat(SamplingMode.explicit(environment, property)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"bogus,", ",", "'  ',"})
    void explicit_unsetOrUnknown_isEmpty(String env, String property) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(SamplingMode.ENV_NAME, env);

        assertThat(SamplingMode.explicit(environment, property)).isEmpty();
    }

    private static RequestDefaults defaults(final SamplingMode mode) {
        return new RequestDefaults(ReasoningPlan.stored(ReasoningLevel.OFF), mode);
    }
}
