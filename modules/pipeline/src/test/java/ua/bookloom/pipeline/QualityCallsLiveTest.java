package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;
import ua.bookloom.pipeline.reviewer.ReviewedPair;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * Proves the reviewer and self-heal calls against a REAL local model, in both provider dialects — the unit
 * tests (WireMock/{@link ScriptedChatModel}) only prove our side of the wire; this proves the real model's reply
 * still fits the shapes {@link ReviewerCall}/{@link DirectedFix} read
 * ({@code .claude/rules/testing.md} "SHOULD add a liveLocal-tagged case per provider-related feature"). A real
 * model's wording is non-deterministic, so every assertion here is structural only.
 */
@Tag("liveLocal")
class QualityCallsLiveTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD_TARGET = "Він відчинив старі двері.";
    private static final String MASKED_SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";
    private static final String REJECTED_TARGET = "HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.";

    @ParameterizedTest
    @EnumSource(Provider.class)
    void reviewerAndDirectedFix_realModel_matchStructure(final Provider provider) {
        Assumptions.assumeTrue(provider.urlConfigured(), provider.name() + ": " + provider.urlEnv + " is not set");

        final ModelCalls calls = provider.calls();
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        final ObjectMapper mapper = new ObjectMapper();
        final PromptTemplates templates = new PromptTemplates();
        final DraftReplyParser replyParser = new DraftReplyParser(mapper);
        final ReviewerCall reviewerCall = new ReviewerCall(templates, new ReviewReplyParser(mapper));
        final DirectedFix directedFix = new DirectedFix(templates, replyParser);

        assertReviewer(reviewerCall, frame, calls);
        assertDirectedFix(directedFix, frame, calls);
    }

    private static void assertReviewer(final ReviewerCall reviewerCall, final CallFrame frame, final ModelCalls calls) {
        final List<ReviewedPair> pairs = List.of(
                new ReviewedPair("s1", SOURCE, GOOD_TARGET),
                new ReviewedPair("s2", "It was a dark night.", "IT WAS A DARK NIGHT."));

        final Result<ReviewVerdict> result = reviewerCall.review(pairs, frame, List.of(), ReviewPass.FIRST, calls);

        assertThat(result.isOk()).as("review result: " + result.error()).isTrue();
        final ReviewVerdict verdict = Objects.requireNonNull(result.data());
        assertThat(verdict.readable()).as("review verdict: " + verdict).isTrue();
        assertThat(verdict.items())
                .as("review verdict: " + verdict)
                .allSatisfy(item -> assertThat(item.segmentId()).isIn("s1", "s2"));
    }

    private static void assertDirectedFix(
            final DirectedFix directedFix, final CallFrame frame, final ModelCalls calls) {
        final QaFinding echoFinding =
                new QaFinding("language", Severity.MEDIUM, "echo similarity 1.0 at or above 0.9", "echo");

        final Result<RepairReply> result =
                directedFix.fix(segment(), frame, MASKED_SOURCE, REJECTED_TARGET, List.of(echoFinding), calls);

        assertThat(result.isOk()).as("directed fix result: " + result.error()).isTrue();
        assertThat(result.data())
                .as("directed fix reply: " + result.data())
                .isInstanceOfSatisfying(
                        RepairReply.Rewritten.class,
                        rewritten -> assertThat(rewritten.maskedTarget()).isNotBlank());
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    /** One configured real local provider: its env-var names, default model, and how to build its base URL. */
    // baseUrl's UnaryOperator is a stateless pure function; the checker cannot see that without an annotation
    // library dependency this test module does not otherwise need.
    @SuppressWarnings("ImmutableEnumChecker")
    private enum Provider {
        OLLAMA(
                "BOOKLOOM_LIVE_OLLAMA_URL",
                "BOOKLOOM_LIVE_OLLAMA_MODEL",
                "gemma4:e4b-mlx",
                ProviderKind.OLLAMA,
                UnaryOperator.identity()),
        LM_STUDIO(
                "BOOKLOOM_LIVE_LMSTUDIO_URL",
                "BOOKLOOM_LIVE_LMSTUDIO_MODEL",
                "google/gemma-4-e4b",
                ProviderKind.OPENAI_COMPATIBLE,
                origin -> origin.resolve("/v1"));

        private final String urlEnv;
        private final String modelEnv;
        private final String defaultModel;
        private final ProviderKind kind;
        private final UnaryOperator<URI> baseUrl;

        Provider(
                final String urlEnv,
                final String modelEnv,
                final String defaultModel,
                final ProviderKind kind,
                final UnaryOperator<URI> baseUrl) {
            this.urlEnv = urlEnv;
            this.modelEnv = modelEnv;
            this.defaultModel = defaultModel;
            this.kind = kind;
            this.baseUrl = baseUrl;
        }

        boolean urlConfigured() {
            final String value = System.getenv(urlEnv);
            return value != null && !value.isBlank();
        }

        /** Builds a real {@link ChatModel} through {@link LlmModule}/{@link ChatModelFactory}, then wraps it as a
         * {@link ModelCalls} seam. */
        ModelCalls calls() {
            final Injector injector = Guice.createInjector(new LlmModule());
            final String id = name().toLowerCase(Locale.ROOT);
            final ProviderConfig config = new ProviderConfig(
                    id,
                    kind,
                    baseUrl.apply(URI.create(Objects.requireNonNull(System.getenv(urlEnv), urlEnv))),
                    ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                    ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
            final Result<ProviderConfig> registered =
                    injector.getInstance(ProviderConfigs.class).register(config);
            assertThat(registered.isOk())
                    .as("provider registration: " + registered.error())
                    .isTrue();
            final String modelId = System.getenv().getOrDefault(modelEnv, defaultModel);
            final Result<ChatModel> model =
                    injector.getInstance(ChatModelFactory.class).create(new ModelSelection(id, modelId));
            assertThat(model.isOk()).as("chat model creation: " + model.error()).isTrue();
            final ChatModel chatModel = Objects.requireNonNull(model.data());
            return (callKind, segmentId, request) -> chatModel.chat(request);
        }
    }
}
