package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.prompt.DraftSchema;

/** The per-segment prompt, whitespace, placeholder gate and reply decision table. */
class SegmentTranslatorTest {

    private static final String MARKED_SOURCE = "He opened the *old* door.";
    private static final String MASKED_SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // A known source language and masked source are placed in the catalog's ordered prompt messages.
    @Test
    void translate_knownSource_recordsExactSystemAndUserMessages() {
        final Segment segment = markdownSegment(MARKED_SOURCE, null);
        final ScriptedChatModel model = acceptingModel();
        TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);
        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.messages().getFirst().content()).contains("from English (en) into Ukrainian (uk)");
            assertThat(request.messages().get(1).content())
                    .contains("<Text>\n" + MASKED_SOURCE + "\n</Text>")
                    .doesNotContain("\"id\":", "\"source\":");
        });
    }

    // An unknown source language tells the model to infer the segment language instead of inventing one.
    @Test
    void translate_unknownSource_recordsExactSystemPromptWithoutSource() {
        final Segment segment = markdownSegment(MARKED_SOURCE, null);
        final ScriptedChatModel model = acceptingModel();
        TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", null)
                .translate(segment);
        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains("from the language of this segment (infer it from its text) into Ukrainian (uk)");
    }

    // The already-resolved requested source is used even when the opened book declares another language.
    @Test
    void translate_resolvedRequestedSource_recordsRequestedLanguageInsteadOfDeclaredLanguage() {
        final Segment segment = markdownSegment(MARKED_SOURCE, "en");
        final ScriptedChatModel model = acceptingModel();
        TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "de")
                .translate(segment);
        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains("from German (de) into Ukrainian (uk)")
                .doesNotContain("from English (en)");
    }

    // Each draft request must constrain the provider to the catalog's low-variance JSON response contract.
    @Test
    void translate_segment_requestsDraftTemperatureAndSchema() {
        final Segment segment = markdownSegment(MARKED_SOURCE, null);
        final ScriptedChatModel model = acceptingModel();
        TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);
        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.temperature()).isEqualTo(0.2);
            assertThat(request.responseFormat()).isEqualTo(new ResponseFormat("draft_translation", DraftSchema.SCHEMA));
            assertThat(request.reasoningEnabled()).isFalse();
        });
    }

    // The required target JSON is parsed before the existing placeholder restoration gate runs.
    @Test
    void translate_documentedJsonReply_restoresAndAcceptsTranslation() {
        final Segment segment = markdownSegment(MARKED_SOURCE, null);
        final ScriptedChatModel model = response("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.", FinishReason.STOP);
        final Result<Decision> result = TranslationJobTestSupport.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);
        assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decisionOf(result).segment().targetInner()).isEqualTo("Він відчинив *старі* двері.");
    }

    // A second strict target reply also restores its protected markdown range.
    @Test
    void translate_mapReply_restoresAndAcceptsTranslation() {
        final Segment segment = markdownSegment(MARKED_SOURCE, null);
        final ScriptedChatModel model = response("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.", FinishReason.STOP);
        final Result<Decision> result = TranslationJobTestSupport.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);
        assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decisionOf(result).segment().targetInner()).isEqualTo("Він відчинив *старі* двері.");
    }

    // Source leading and trailing whitespace wins over the model reply's surrounding whitespace.
    @Test
    void translate_differentReplyWhitespace_restoresSourceWhitespace() {
        final Segment segment = txtSegment("  Hello world\n");
        final ScriptedChatModel model = response("\nHELLO WORLD  ", FinishReason.STOP);

        final Result<Decision> result = TranslationJobTestSupport.segmentTranslator(
                        documents, model, BookFormat.TXT, "uk", "en")
                .translate(segment);

        assertThat(decisionOf(result).segment().targetInner()).isEqualTo("  HELLO WORLD\n");
        assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    // Each D4 row either accepts restored markup, flags and discards a candidate, or stops with the original error.
    @ParameterizedTest(name = "{0}")
    @MethodSource("decisionCases")
    void translate_replyDecisionTable_returnsExpectedOutcome(
            final String name,
            final ScriptedChatModel model,
            final UnaryOperator<DocumentPort> portDecorator,
            final Consumer<Result<Decision>> expectation) {
        final Segment segment = markdownSegment(MARKED_SOURCE, null);

        final Result<Decision> result = TranslationJobTestSupport.segmentTranslator(
                        portDecorator.apply(documents), model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        expectation.accept(result);
        assertThat(model.requests()).hasSize(name.equals("missing g1") ? 2 : 1);
    }

    // A thrown middle outcome consumes one queue item and does not prevent a later scripted result from being used.
    @Test
    void translate_scriptedResultExceptionResult_consumesQueueAndRecordsRequestOrder() {
        final Segment first = markdownSegment("First.", null, "First.md");
        final Segment second = markdownSegment("Second.", null, "Second.md");
        final Segment third = markdownSegment("Third.", null, "Third.md");
        final IllegalStateException failure = new IllegalStateException("middle call failed");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply("FIRST."), FinishReason.STOP)))
                .throwFailure(failure)
                .answer(Result.ok(
                        new ChatResponse(TranslationJobTestSupport.targetReply("THIRD."), FinishReason.STOP)));
        final SegmentTranslator translator =
                TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", null);

        final Result<Decision> firstResult = translator.translate(first);
        final Result<Decision> secondResult = translator.translate(second);
        final Result<Decision> thirdResult = translator.translate(third);

        assertThat(decisionOf(firstResult).segment().targetInner()).isEqualTo("FIRST.");
        assertThat(Objects.requireNonNull(secondResult.error()).cause()).isSameAs(failure);
        assertThat(decisionOf(thirdResult).segment().targetInner()).isEqualTo("THIRD.");
        assertThat(model.requests())
                .extracting(request -> request.messages().get(1).content())
                .allSatisfy(message -> assertThat(message).contains("<Text>"));
    }

    private static Stream<Arguments> decisionCases() {
        return Stream.concat(
                normalResponseCases(),
                Stream.concat(invalidResponseCases(), Stream.concat(modelErrorCases(), boundaryErrorCases())));
    }

    private static Stream<Arguments> normalResponseCases() {
        return Stream.of(
                arguments(
                        "valid STOP",
                        response("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.", FinishReason.STOP),
                        UnaryOperator.identity(),
                        accepted("HE OPENED THE *OLD* DOOR.")),
                arguments(
                        "missing g1",
                        responseThen("HE OPENED THE ⟦g0⟧OLD DOOR.", "HE OPENED THE ⟦g0⟧OLD DOOR."),
                        UnaryOperator.identity(),
                        flagged(ErrorCode.validation)));
    }

    private static Stream<Arguments> invalidResponseCases() {
        return Stream.of(
                arguments(
                        "empty STOP",
                        response("", FinishReason.STOP),
                        UnaryOperator.identity(),
                        flagged(ErrorCode.emptyCompletion)),
                arguments(
                        "whitespace OTHER",
                        response("  \n", FinishReason.OTHER),
                        UnaryOperator.identity(),
                        flagged(ErrorCode.emptyCompletion)),
                arguments(
                        "LENGTH",
                        response("HE OPENED", FinishReason.LENGTH),
                        UnaryOperator.identity(),
                        flagged(ErrorCode.validation)),
                arguments(
                        "OTHER",
                        response("HE OPENED", FinishReason.OTHER),
                        UnaryOperator.identity(),
                        flagged(ErrorCode.validation)));
    }

    private static Stream<Arguments> modelErrorCases() {
        final AppError validation = error(ErrorCode.validation);
        final AppError empty = error(ErrorCode.emptyCompletion);
        final AppError context = error(ErrorCode.contextWindow);
        final AppError cancelled = error(ErrorCode.cancelled);
        final AppError unreachable = error(ErrorCode.unreachable);
        final AppError auth = error(ErrorCode.auth);
        return Stream.of(
                arguments(
                        "model validation", modelError(validation), UnaryOperator.identity(), flaggedSame(validation)),
                arguments("model empty completion", modelError(empty), UnaryOperator.identity(), flaggedSame(empty)),
                arguments("model context window", modelError(context), UnaryOperator.identity(), flaggedSame(context)),
                arguments("model cancelled", modelError(cancelled), UnaryOperator.identity(), terminalSame(cancelled)),
                arguments(
                        "model unreachable",
                        modelError(unreachable),
                        UnaryOperator.identity(),
                        terminalSame(unreachable)),
                arguments("model auth", modelError(auth), UnaryOperator.identity(), terminalSame(auth)));
    }

    private static Stream<Arguments> boundaryErrorCases() {
        final AppError unmaskInternal = error(ErrorCode.internal);
        final IllegalStateException thrown = new IllegalStateException("model exploded once");
        return Stream.of(
                arguments(
                        "model throws",
                        new ScriptedChatModel().throwFailure(thrown),
                        UnaryOperator.identity(),
                        terminalInternalWithCause(thrown)),
                arguments(
                        "unmask internal",
                        response("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.", FinishReason.STOP),
                        port -> failingUnmask(port, unmaskInternal),
                        terminalSame(unmaskInternal)));
    }

    private Segment markdownSegment(final String content, @Nullable final String language) {
        return markdownSegment(content, language, "Book.md");
    }

    private Segment markdownSegment(final String content, @Nullable final String language, final String fileName) {
        return onlySegment(TestBooks.markdown(tempDir.resolve(fileName), content, language));
    }

    private Segment txtSegment(final String content) {
        final Segment parsed = onlySegment(TestBooks.txt(tempDir.resolve("Book.txt"), content));
        return new Segment(
                parsed.id(),
                parsed.unit(),
                parsed.order(),
                parsed.kind(),
                content,
                content,
                parsed.placeholders(),
                parsed.sourceHash(),
                parsed.prevKey(),
                parsed.nextKey(),
                parsed.anchor(),
                parsed.targetInner(),
                parsed.status(),
                parsed.confidence());
    }

    private Segment onlySegment(final Path path) {
        final Document document = Objects.requireNonNull(documents.open(path).data(), "opened document");
        return document.units().getFirst().segments().getFirst();
    }

    private static ScriptedChatModel acceptingModel() {
        return response("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.", FinishReason.STOP);
    }

    private static ScriptedChatModel response(final String content, final FinishReason finish) {
        return new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(
                        content.isBlank() ? content : TranslationJobTestSupport.targetReply(content), finish)));
    }

    private static ScriptedChatModel responseThen(final String first, final String second) {
        return new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(first), FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(second), FinishReason.STOP)));
    }

    private static ScriptedChatModel modelError(final AppError error) {
        return new ScriptedChatModel().answer(Result.err(error));
    }

    private static AppError error(final ErrorCode code) {
        return AppError.of(code, "Test error", "The scripted model failed.");
    }

    private static Arguments arguments(
            final String name,
            final ScriptedChatModel model,
            final UnaryOperator<DocumentPort> decorator,
            final Consumer<Result<Decision>> expectation) {
        return Arguments.of(name, model, decorator, expectation);
    }

    private static Consumer<Result<Decision>> accepted(final String target) {
        return result -> {
            assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
            assertThat(decisionOf(result).segment().targetInner()).isEqualTo(target);
            assertThat(decisionOf(result).flagReason()).isNull();
        };
    }

    private static Consumer<Result<Decision>> flagged(final ErrorCode code) {
        return result -> {
            assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.FLAGGED);
            assertThat(decisionOf(result).segment().targetInner()).isNull();
            assertThat(decisionOf(result).flagReason()).hasFieldOrPropertyWithValue("code", code);
        };
    }

    private static Consumer<Result<Decision>> flaggedSame(final AppError error) {
        return result -> {
            flagged(error.code()).accept(result);
            assertThat(decisionOf(result).flagReason()).isSameAs(error);
        };
    }

    private static Consumer<Result<Decision>> terminalSame(final AppError error) {
        return result -> {
            assertThat(result.data()).isNull();
            assertThat(result.error()).isSameAs(error);
        };
    }

    private static Consumer<Result<Decision>> terminalInternalWithCause(final Throwable cause) {
        return result -> {
            assertThat(result.data()).isNull();
            assertThat(result.error()).hasFieldOrPropertyWithValue("code", ErrorCode.internal);
            assertThat(Objects.requireNonNull(result.error()).cause()).isSameAs(cause);
        };
    }

    private static Decision decisionOf(final Result<Decision> result) {
        return Objects.requireNonNull(result.data(), "decision");
    }

    private static DocumentPort failingUnmask(final DocumentPort delegate, final AppError error) {
        return new DocumentPort() {
            @Override
            public Result<Document> open(final Path source) {
                return delegate.open(source);
            }

            @Override
            public Result<Path> write(final Document document, final Path destination, final String targetLanguage) {
                return delegate.write(document, destination, targetLanguage);
            }

            @Override
            public Result<Boolean> close(final Document document) {
                return delegate.close(document);
            }

            @Override
            public Result<String> unmask(
                    final BookFormat format, final Segment segment, final String translatedMasked) {
                return Result.err(error);
            }
        };
    }
}
