package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import ua.bookloom.document.DocumentModule;

/** Covers recovery and one-repair handling for structured replies at the document boundary. */
class StructuredReplySegmentTranslatorTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // A malformed prefix is rejected rather than recovered from wrapper prose.
    @Test
    void translate_malformedPrefixWithCompleteEnvelope_repairsWithoutUnmaskingWrapperText() {
        final Segment segment = segment();
        final ScriptedChatModel model = response(
                "Here is {\"target\":\"Він відчинив ⟦g0⟧старі⟦g1⟧ двері.\"}",
                "{\"target\":\"Він відчинив ⟦g0⟧старі⟦g1⟧ двері.\"}");

        final Result<Decision> result = translator(documents, model).translate(segment);

        assertThat(decision(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decision(result).segment().targetInner()).isEqualTo("Він відчинив *старі* двері.");
        assertThat(model.requests()).hasSize(2);
    }

    // The first malformed envelope is retried with a clean correction instruction and a valid repair is accepted.
    @Test
    void translate_invalidStructuredReply_validRepairAcceptsSecondReply() {
        final Segment segment = segment();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"segments\":", FinishReason.STOP)))
                .answer(Result.ok(
                        new ChatResponse("{\"target\":\"Він відчинив ⟦g0⟧старі⟦g1⟧ двері.\"}", FinishReason.STOP)));

        final Result<Decision> result = translator(documents, model).translate(segment);

        assertThat(decision(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("<RejectedReply>")
                .contains("{\"segments\":")
                .contains("was not valid JSON")
                .contains("start with { followed immediately by \"target\"")
                .contains("Never reproduce a rejected prefix");
    }

    // A second malformed envelope is flagged before the document placeholder hard gate can see it.
    @Test
    void translate_secondInvalidStructuredReply_flagsWithoutCallingUnmask() {
        final Segment segment = segment();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"segments\":", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse("{\"target\":", FinishReason.STOP)));
        final AtomicBoolean unmaskCalled = new AtomicBoolean();

        final Result<Decision> result =
                translator(unmaskRecordingPort(documents, unmaskCalled), model).translate(segment);

        assertThat(decision(result).segment().status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decision(result).flagReason()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(unmaskCalled.get()).isFalse();
    }

    // A structured reply with no target receives one clean repair attempt, then remains a validation failure.
    @Test
    void translate_jsonReplyWithoutTarget_repairsThenFlagsValidation() {
        final Segment segment = segment();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"target\":\"\"}", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse("{\"target\":", FinishReason.STOP)));

        final Result<Decision> result = translator(documents, model).translate(segment);

        assertThat(decision(result).segment().status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decision(result).flagReason()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).hasSize(2);
    }

    private SegmentTranslator translator(DocumentPort port, ScriptedChatModel model) {
        return TranslationJobTestSupport.segmentTranslator(port, model, BookFormat.MARKDOWN, "uk", "en");
    }

    private Segment segment() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.", null);
        return Objects.requireNonNull(documents.open(source).data(), "document")
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }

    private static ScriptedChatModel response(String first, String second) {
        return new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(first, FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(second, FinishReason.STOP)));
    }

    private static Decision decision(Result<Decision> result) {
        return Objects.requireNonNull(result.data(), "decision");
    }

    private static DocumentPort unmaskRecordingPort(DocumentPort delegate, AtomicBoolean called) {
        return new DocumentPort() {
            @Override
            public Result<Document> open(Path source) {
                return delegate.open(source);
            }

            @Override
            public Result<Path> write(Document document, Path destination, String targetLanguage) {
                return delegate.write(document, destination, targetLanguage);
            }

            @Override
            public Result<Boolean> close(Document document) {
                return delegate.close(document);
            }

            @Override
            public Result<String> unmask(BookFormat format, Segment segment, String translatedMasked) {
                called.set(true);
                return delegate.unmask(format, segment, translatedMasked);
            }
        };
    }
}
