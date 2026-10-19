package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.reviewer.ReviewItem;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewStatus;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;
import ua.bookloom.pipeline.reviewer.ReviewedPair;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * A draft then a reviewer call each reach the wire — on both the OpenAI-compatible and the Ollama-native dialect — at
 * their own temperature, reasoning off, schema and output cap; the reviewer states its fixed seed and reads an edit
 * reply from the wire; and a reviewer call that timed out is sent again without any response format.
 */
class ReviewerWireMockTest {

    private static final ReviewerCall REVIEWER_CALL =
            new ReviewerCall(new PromptTemplates(), new ReviewReplyParser(new ObjectMapper()));
    private static final String SOURCE_TEXT = "He opened the old door.";
    private static final String DRAFT_TARGET = "Він відчинив старі двері.";
    private static final String REVIEWER_REPLY = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":["
            + "{\"criterion\":\"gender\",\"quote\":\"Він відчинив\",\"replacement\":\"Вона відчинила\"}]}]}";
    private static final String THINK_OFF = "\"think\":false";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_draftThenReview_eachPostsItsOwnTemperatureSeedReasoningAndSchema(final ProviderKind kind) {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Segment segment = firstSegment(documents);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.target(DRAFT_TARGET, Duration.ZERO), provider.reply(REVIEWER_REPLY, Duration.ZERO)));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});
            DraftStepFixtures.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en")
                    .translate(segment);

            final Result<ReviewVerdict> reviewed = REVIEWER_CALL.review(
                    List.of(new ReviewedPair(segment.id(), segment.masked(), DRAFT_TARGET)),
                    FRAME,
                    List.of(),
                    ReviewPass.FIRST,
                    (callKind, segmentId, request) -> model.chat(request));

            assertDraftThenReviewBodies(kind, provider.chatBodies());
            assertThat(Objects.requireNonNull(reviewed.data()).items())
                    .singleElement()
                    .extracting(ReviewItem::status)
                    .isEqualTo(ReviewStatus.EDITS);
        }
    }

    private static void assertDraftThenReviewBodies(final ProviderKind kind, final List<String> bodies) {
        assertThat(bodies.get(0)).contains("\"temperature\":0.2", "\"target\"").doesNotContain("\"seed\"");
        assertThat(bodies.get(1))
                .contains("\"temperature\":0.0", "\"seed\":15", "\"edits\"")
                .doesNotContain("\"target\"");
        assertReasoningOff(kind, bodies);
        assertThat(bodies.get(0)).contains(capField(kind) + ":128");
        assertThat(bodies.get(1)).contains(capField(kind) + ":");
    }

    // A structured call that timed out is sent once more with no response_format/format field, because one provider
    // never answered the schema-constrained call that the same prompt without a schema answered in seconds.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void review_firstCallTimesOut_theResendCarriesNoResponseFormatOnTheWire(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.reply(REVIEWER_REPLY, Duration.ZERO));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});
            final AtomicInteger sent = new AtomicInteger();
            final ModelCalls timingOutOnce = (callKind, segmentId, request) -> sent.getAndIncrement() == 0
                    ? Result.err(AppError.of(ErrorCode.timeout, "Model failure", "no answer"))
                    : model.chat(request);

            final Result<ReviewVerdict> reviewed = REVIEWER_CALL.review(
                    List.of(new ReviewedPair("Book.md:0", SOURCE_TEXT, DRAFT_TARGET)),
                    FRAME,
                    List.of(),
                    ReviewPass.FIRST,
                    timingOutOnce);

            assertThat(provider.chatBodies()).singleElement().satisfies(body -> {
                assertThat(body).doesNotContain(formatField(kind));
                assertThat(body).contains("\"temperature\":0.0", "\"seed\":15");
            });
            assertThat(Objects.requireNonNull(reviewed.data()).items()).hasSize(1);
        }
    }

    // finish_reason "length" (OpenAI-compatible) and done_reason "length" (Ollama) both mean the cap cut the reply.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void review_replyCutByTheCapOnTheWire_isReAskedForTheUnreadPairAndNeverUnavailable(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.replyCut(
                            "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s2\",\"status\":\"edits\",\"ed"),
                    provider.reply(REVIEWER_REPLY, Duration.ZERO)));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});

            final ReviewVerdict verdict = Objects.requireNonNull(REVIEWER_CALL
                    .review(
                            List.of(
                                    new ReviewedPair("Book.md:0", SOURCE_TEXT, DRAFT_TARGET),
                                    new ReviewedPair("Book.md:1", SOURCE_TEXT, "Він відчинив двері.")),
                            FRAME,
                            List.of(),
                            ReviewPass.FIRST,
                            (callKind, segmentId, request) -> model.chat(request))
                    .data());

            assertThat(provider.chatBodies()).hasSize(2);
            assertThat(provider.chatBodies().get(1))
                    .contains("Він відчинив двері.")
                    .doesNotContain(DRAFT_TARGET);
            assertThat(verdict.isUnavailable()).isFalse();
            assertThat(verdict.items()).extracting(ReviewItem::segmentId).containsExactly("Book.md:0", "Book.md:1");
            assertThat(verdict.itemFor("Book.md:1"))
                    .hasValueSatisfying(item -> assertThat(item.status()).isEqualTo(ReviewStatus.EDITS));
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void review_firstCall_carriesTheResponseFormatOnTheWire(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.reply(REVIEWER_REPLY, Duration.ZERO));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});

            final Result<ReviewVerdict> reviewed = REVIEWER_CALL.review(
                    List.of(new ReviewedPair("Book.md:0", SOURCE_TEXT, DRAFT_TARGET)),
                    FRAME,
                    List.of(),
                    ReviewPass.FIRST,
                    (callKind, segmentId, request) -> model.chat(request));

            assertThat(reviewed.error()).as("review result").isNull();
            assertThat(provider.chatBodies())
                    .singleElement()
                    .satisfies(body ->
                            assertThat(body).contains(formatField(kind), "\"enum\":[\"ok\",\"edits\",\"rewrite\"]"));
        }
    }

    private Segment firstSegment(final DocumentPort documents) {
        return Objects.requireNonNull(documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), SOURCE_TEXT))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }

    private static String formatField(final ProviderKind kind) {
        return switch (kind) {
            case OLLAMA -> "\"format\":{";
            case OPENAI_COMPATIBLE -> "\"response_format\"";
        };
    }

    private static String capField(final ProviderKind kind) {
        return switch (kind) {
            case OLLAMA -> "\"num_predict\"";
            case OPENAI_COMPATIBLE -> "\"max_tokens\"";
        };
    }

    private static void assertReasoningOff(final ProviderKind kind, final List<String> bodies) {
        switch (kind) {
            case OLLAMA ->
                assertThat(bodies).allSatisfy(body -> assertThat(body).contains(THINK_OFF));
            case OPENAI_COMPATIBLE ->
                assertThat(bodies).allSatisfy(body -> assertThat(body).doesNotContain("\"think\"", "\"reasoning\""));
        }
    }
}
