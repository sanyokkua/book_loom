package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.util.Modules;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProbeVerdicts.Outcome;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/**
 * Proves on the wire what the Advanced features stage sends to a server of unknown make and what verdict each reply
 * earns on the model's settings.
 */
class AdvancedChecksTest {

    private static final String PROVIDER = "office";
    private static final String MODEL = "mara-7b";
    private static final String CHAT = "/v1/chat/completions";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final FakeProviderConfigs configs = new FakeProviderConfigs();
    private ModelSettingsRepository settings = new FakeModelSettingsRepository();

    @BeforeEach
    void startServer() {
        server.start();
        assertThat(configs.save(new ProviderConfig(
                                PROVIDER,
                                ProviderKind.OPENAI_COMPATIBLE,
                                URI.create(server.baseUrl() + "/v1"),
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(5)))
                        .isOk())
                .isTrue();
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"mara-7b\",\"object\":\"model\"}]}")));
        stubChat(post(urlEqualTo(CHAT)).atPriority(10), "Fine.", "stop", 2);
        stubChat(chatContaining("verification_probe"), "{\"status\":\"ok\"}", "stop", 5);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void schema_replyExactlyTheEnum_isHonoured() {
        stubChat(chatContaining("advanced_check"), "{\"k\":\"ZX-91\"}", "stop", 7);

        final StageOutcome advanced = advancedStage();

        assertThat(advanced.status()).isEqualTo(StageStatus.PASSED);
        final ProbeVerdicts.Verdict schema = verdicts().answerSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.outcome()).isEqualTo(Outcome.HONOURED);
        assertThat(schema.source()).isEqualTo("test");
        server.verify(postRequestedFor(urlEqualTo(CHAT))
                .withRequestBody(matchingJsonPath(
                        "$.response_format.json_schema.schema.properties.k.enum[0]", equalTo("ZX-91"))));
    }

    @Test
    void schema_freeTextReply_isIgnoredAndNotRetriedAsJsonObject() {
        stubChat(chatContaining("advanced_check"), "Sure, here it is: ZX-91", "stop", 7);

        advancedStage();

        assertThat(Objects.requireNonNull(verdicts().answerSchema()).outcome()).isEqualTo(Outcome.IGNORED);
        server.verify(0, postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("json_object")));
    }

    @Test
    void schema_http400_isRefused() {
        server.stubFor(chatContaining("advanced_check")
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"response_format is not supported\","
                                + "\"type\":\"invalid_request_error\"}}")));

        final StageOutcome advanced = advancedStage();

        assertThat(advanced.status()).isEqualTo(StageStatus.PASSED);
        assertThat(Objects.requireNonNull(verdicts().answerSchema()).outcome()).isEqualTo(Outcome.REFUSED);
        server.verify(1, postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("advanced_check")));
    }

    @Test
    void thinkingOff_firstSwitchEmptiesReasoning_isStoredByName() {
        server.stubFor(chatContaining("Think step by step")
                .atPriority(4)
                .willReturn(okJson(reply("42", "stop", 30, "Let me add 17 and 25 first..."))));
        server.stubFor(chatContaining("Think step by step")
                .withRequestBody(containing("\"chat_template_kwargs\":{\"enable_thinking\":false}"))
                .atPriority(1)
                .willReturn(okJson(reply("42", "stop", 2, null))));

        advancedStage();

        final ProbeVerdicts.ThinkingVerdict thinking = verdicts().thinkingSwitch();
        assertThat(thinking).isNotNull();
        assertThat(thinking.switchName()).isEqualTo("chat_template_kwargs.enable_thinking");
        assertThat(thinking.verdict().outcome()).isEqualTo(Outcome.HONOURED);
        server.verify(
                1,
                postRequestedFor(urlEqualTo(CHAT))
                        .withRequestBody(containing("Think step by step"))
                        .withRequestBody(containing("\"reasoning_effort\":\"none\"")));
        server.verify(0, postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("\"reasoning\":{")));
    }

    @Test
    void extraSamplers_identicalGreedyReplies_isHonoured() {
        stubChat(chatContaining("lighthouse"), "The lighthouse keeps its quiet watch.", "stop", 8);

        advancedStage();

        assertThat(Objects.requireNonNull(verdicts().extraSamplers()).outcome()).isEqualTo(Outcome.HONOURED);
        server.verify(
                2,
                postRequestedFor(urlEqualTo(CHAT))
                        .withRequestBody(containing("lighthouse"))
                        .withRequestBody(containing("\"top_k\":1"))
                        .withRequestBody(containing("\"temperature\":1.5")));
    }

    @Test
    void capField_completionTokensNearSixteen_choosesMaxCompletionTokens() {
        stubChat(chatContaining("Count from 1 to 100"), "1 2 3 4 5 6 7 8", "length", 16);

        advancedStage();

        assertThat(Objects.requireNonNull(verdicts().capField()).outcome()).isEqualTo(Outcome.HONOURED);
        assertThat(stored().outputField()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
        server.verify(
                1,
                postRequestedFor(urlEqualTo(CHAT))
                        .withRequestBody(containing("Count from 1 to 100"))
                        .withRequestBody(containing("\"max_completion_tokens\":16")));
    }

    @Test
    void capField_completionFieldIgnoredButMaxTokensLimits_choosesMaxTokens() {
        stubChat(chatContaining("Count from 1 to 100"), "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18", "stop", 60);
        server.stubFor(chatContaining("Count from 1 to 100")
                .withRequestBody(containing("\"max_tokens\":16"))
                .atPriority(1)
                .willReturn(okJson(reply("1 2 3 4 5 6 7 8", "length", 16, null))));

        advancedStage();

        assertThat(Objects.requireNonNull(verdicts().capField()).outcome()).isEqualTo(Outcome.IGNORED);
        assertThat(stored().outputField()).isEqualTo(OutputLengthField.MAX_TOKENS);
    }

    @Test
    void advanced_savesVerdictsWithSourceTestAndKeepsAConcurrentRefusal() {
        final RefusedSetting refusal = new RefusedSetting("seed", LocalDate.of(2026, 10, 29), "left it out");
        settings = new RefusalMeanwhile(new FakeModelSettingsRepository(), refusal);
        stubChat(chatContaining("advanced_check"), "{\"k\":\"ZX-91\"}", "stop", 7);

        advancedStage();

        assertThat(stored().refused()).containsExactly(refusal);
        final ProbeVerdicts kept = stored().probeVerdicts();
        assertThat(List.of(
                        Objects.requireNonNull(kept.answerSchema()).source(),
                        Objects.requireNonNull(kept.thinkingSwitch()).verdict().source(),
                        Objects.requireNonNull(kept.extraSamplers()).source(),
                        Objects.requireNonNull(kept.capField()).source()))
                .containsOnly("test");
    }

    @Test
    void advanced_llamaServer_runsOnlySchemaAndThinking() {
        server.stubFor(get(urlEqualTo("/health"))
                .willReturn(okJson("{\"status\":\"ok\"}").withHeader("Server", "llama.cpp")));
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson("{\"build_info\":\"b11669-620d2225\"}")));

        advancedStage();

        assertThat(verdicts().answerSchema()).isNotNull();
        assertThat(verdicts().thinkingSwitch()).isNotNull();
        assertThat(verdicts().extraSamplers()).isNull();
        assertThat(verdicts().capField()).isNull();
        server.verify(0, postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("lighthouse")));
    }

    @Test
    void full_withoutAdvancedPolicy_sendsNoAdvancedCall() {
        final VerificationReport report = report(VerificationPolicy.FULL);

        assertThat(report.stages()).extracting(StageOutcome::stage).doesNotContain(VerificationStage.ADVANCED);
        server.verify(0, postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("advanced_check")));
        assertThat(verdicts()).isEqualTo(ProbeVerdicts.none());
    }

    @Test
    void inference_structuredProbeAnsweredOtherwise_storesSchemaIgnoredFromTest() {
        stubChat(chatContaining("verification_probe"), "{\"state\":\"fine\"}", "stop", 5);

        final VerificationReport report = report(VerificationPolicy.FULL);

        assertThat(report.stages().getLast().note()).isEqualTo("structured output: not confirmed");
        final ProbeVerdicts.Verdict schema = verdicts().answerSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.outcome()).isEqualTo(Outcome.IGNORED);
        assertThat(schema.source()).isEqualTo("test");
    }

    private StageOutcome advancedStage() {
        final VerificationReport report = report(VerificationPolicy.ADVANCED);
        assertThat(report.stages())
                .extracting(StageOutcome::stage)
                .containsExactly(
                        VerificationStage.CONNECTION,
                        VerificationStage.MODELS,
                        VerificationStage.INFERENCE,
                        VerificationStage.ADVANCED);
        return report.stages().getLast();
    }

    private VerificationReport report(final VerificationPolicy policy) {
        final Result<VerificationReport> result = verifier().verify(new ModelSelection(PROVIDER, MODEL), policy);
        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "report");
    }

    private ProviderVerifier verifier() {
        final ModelSettingsRepository repository = settings;
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ProviderConfigs.class).toInstance(configs);
                        bind(ModelSettingsRepository.class).toInstance(repository);
                        bind(RetryPolicy.class).toInstance(new RetryPolicy(FIXED_CLOCK, () -> 0.5, delay -> {}));
                    }
                }))
                .getInstance(ProviderVerifier.class);
    }

    private ModelSettings stored() {
        final Optional<ModelSettings> found =
                Objects.requireNonNull(settings.find(PROVIDER, MODEL).data(), "found");
        return found.orElseThrow();
    }

    private ProbeVerdicts verdicts() {
        return Objects.requireNonNull(settings.find(PROVIDER, MODEL).data(), "found")
                .map(ModelSettings::probeVerdicts)
                .orElse(ProbeVerdicts.none());
    }

    private static MappingBuilder chatContaining(final String text) {
        return post(urlEqualTo(CHAT)).withRequestBody(containing(text)).atPriority(5);
    }

    private void stubChat(final MappingBuilder request, final String content, final String finish, final int tokens) {
        server.stubFor(request.willReturn(okJson(reply(content, finish, tokens, null))));
    }

    private static String reply(
            final String content, final String finish, final int completionTokens, final @Nullable String reasoning) {
        final String reasoningField = reasoning == null ? "" : ",\"reasoning_content\":\"" + reasoning + "\"";
        return "{\"model\":\"mara-7b\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + quoted(content) + reasoningField + "},\"finish_reason\":\"" + finish + "\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":" + completionTokens + "}}";
    }

    private static String quoted(final String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A store where a run records a refusal just before the check's verdicts are written. */
    private record RefusalMeanwhile(ModelSettingsRepository delegate, RefusedSetting refusal)
            implements ModelSettingsRepository {

        @Override
        public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
            return delegate.find(providerId, modelId);
        }

        @Override
        public Result<ModelSettings> save(final ModelSettings value) {
            return delegate.save(value);
        }

        @Override
        public Result<ModelSettings> update(
                final String providerId, final String modelId, final UnaryOperator<ModelSettings> change) {
            delegate.update(providerId, modelId, current -> withRefusal(current, refusal));
            return delegate.update(providerId, modelId, change);
        }

        @Override
        public Result<Boolean> delete(final String providerId, final String modelId) {
            return delegate.delete(providerId, modelId);
        }

        @Override
        public Result<List<ModelSettings>> all() {
            return delegate.all();
        }

        private static ModelSettings withRefusal(final ModelSettings current, final RefusedSetting refusal) {
            return new ModelSettings(
                    current.providerId(),
                    current.modelId(),
                    current.temperatureChoice(),
                    current.temperature(),
                    current.contextWindow(),
                    current.maxOutput(),
                    current.outputField(),
                    current.refused().contains(refusal) ? current.refused() : List.of(refusal),
                    current.measuredSecondsPerSegment(),
                    current.measuredFrom(),
                    current.probeVerdicts());
        }
    }
}
