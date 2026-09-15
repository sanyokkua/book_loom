package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Proves, through a whole job, the decisions the scenario audit found covered only at the translator. */
class TranslationJobDecisionGapsTest {

    private static final String PROMPT_RULES =
            " Preserve every ⟦gN⟧ placeholder exactly as written. Return only the translated text.";

    @TempDir
    private Path tempDir;

    // Swapping the request's and the book's language in the job would send the declared en instead of the chosen de.
    @Test
    void run_requestedSourceLanguage_beatsTheDeclaredLanguage() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.", "en");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.");

        final JobReport report = report(job(TranslationJobTestSupport.documents(), source, "de", model));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests().getFirst().messages().getFirst().content())
                .isEqualTo("Translate the following text from de into uk." + PROMPT_RULES);
    }

    // Without a requested language the job must use the book's declaration, and name none when the book has none.
    @ParameterizedTest(name = "declared={0}")
    @CsvSource(
            delimiter = '|',
            nullValues = "NULL",
            value = {"en|Translate the following text from en into uk.", "NULL|Translate the following text into uk."})
    void run_noRequestedSourceLanguage_usesTheBookDeclaration(
            @Nullable final String declared, final String expectedInstruction) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.", declared);
        final ScriptedChatModel model = TranslationJobTestSupport.replies("ONE.");

        final JobReport report = report(job(TranslationJobTestSupport.documents(), source, null, model));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests().getFirst().messages().getFirst().content())
                .isEqualTo(expectedInstruction + PROMPT_RULES);
    }

    // A flag that stopped the loop would leave the second paragraph unsent and the book unwritten.
    @ParameterizedTest(name = "{0}")
    @MethodSource("flaggingReplies")
    void run_flaggedFirstSegment_stillSendsAndAcceptsTheSecond(
            final String label, final Result<ChatResponse> firstReply, final ErrorCode reason) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.\n\nShe left.\n");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(firstReply)
                .answer(Result.ok(new ChatResponse("SHE LEFT.", FinishReason.STOP)));

        final JobReport report = report(job(TranslationJobTestSupport.documents(), source, "en", model));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.accepted()).isEqualTo(1);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.md:0", reason));
        assertThat(model.requests()).hasSize(2);
    }

    private static Stream<Arguments> flaggingReplies() {
        return Stream.of(
                Arguments.of(
                        "missing token", reply("HE OPENED THE ⟦g0⟧OLD DOOR.", FinishReason.STOP), ErrorCode.validation),
                Arguments.of(
                        "whitespace with a normal finish", reply("  \n", FinishReason.STOP), ErrorCode.emptyCompletion),
                Arguments.of("cut off by length", reply("HE OPENED", FinishReason.LENGTH), ErrorCode.validation),
                Arguments.of("model validation", refusal(ErrorCode.validation), ErrorCode.validation),
                Arguments.of("model emptyCompletion", refusal(ErrorCode.emptyCompletion), ErrorCode.emptyCompletion),
                Arguments.of("model contextWindow", refusal(ErrorCode.contextWindow), ErrorCode.contextWindow));
    }

    // Reporting a thrown model call under any other code would hide an unexpected fault behind a known outcome.
    @Test
    void run_modelCallThrows_endsFailedWithInternalError() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.\n");
        final ScriptedChatModel model = new ScriptedChatModel().throwFailure(new IllegalStateException("model broke"));

        final JobReport report = report(job(TranslationJobTestSupport.documents(), source, "en", model));

        assertThat(report.end()).isEqualTo(JobState.FAILED);
        assertThat(report.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(report.accepted()).isZero();
        assertThat(report.flagged()).isZero();
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // Flagging an unexpected restore failure would export the book as if the paragraph had simply been refused.
    @Test
    void run_unmaskInternalErrorOnFirstSegment_endsFailedWithBothSegmentsPending() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.\n");
        final AppError restoreFault = AppError.of(ErrorCode.internal, "Restore failed", "The restore broke.");
        final DocumentPort port =
                new BookExporterTestSupport.ForwardingDocumentPort(TranslationJobTestSupport.documents()) {
                    @Override
                    public Result<String> unmask(
                            final BookFormat format, final Segment segment, final String translated) {
                        return Result.err(restoreFault);
                    }
                };
        final ScriptedChatModel model = TranslationJobTestSupport.replies("ONE.", "TWO.");

        final JobReport report = report(job(port, source, "en", model));

        assertThat(report.end()).isEqualTo(JobState.FAILED);
        assertThat(report.error()).isEqualTo(restoreFault);
        assertThat(report.segments()).isEqualTo(2);
        assertThat(report.accepted()).isZero();
        assertThat(report.flagged()).isZero();
        assertThat(model.requests()).hasSize(1);
    }

    // Retrying or pausing on a failed write without pause on error would leave the caller waiting on nothing.
    @Test
    void run_exportWriteFailsWithoutPauseOnError_endsFailedWithTheWriteError() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n");
        final AppError writeFault = AppError.of(ErrorCode.internal, "Write failed", "The disk refused the book.");
        final DocumentPort port =
                new BookExporterTestSupport.ForwardingDocumentPort(TranslationJobTestSupport.documents()) {
                    @Override
                    public Result<Path> write(
                            final Document document, final Path destination, final String targetLanguage) {
                        return Result.err(writeFault);
                    }
                };

        final JobReport report = report(job(port, source, "en", TranslationJobTestSupport.replies("ONE.")));

        assertThat(report.end()).isEqualTo(JobState.FAILED);
        assertThat(report.error()).isEqualTo(writeFault);
        assertThat(report.accepted()).isEqualTo(1);
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
        assertThat(tempDir.resolve(".Book.uk.md")).doesNotExist();
    }

    private TranslationJobImpl job(
            final DocumentPort documents,
            final Path source,
            @Nullable final String sourceLanguage,
            final ChatModel model) {
        final TranslationRequest request =
                new TranslationRequest(source, tempDir.resolve("Book.uk.md"), "uk", sourceLanguage, false);
        return new TranslationJobImpl(documents, request, model);
    }

    private static JobReport report(final TranslationJobImpl job) {
        return TranslationJobTestSupport.report(job.run());
    }

    private static Result<ChatResponse> reply(final String content, final FinishReason finish) {
        return Result.ok(new ChatResponse(content, finish));
    }

    private static Result<ChatResponse> refusal(final ErrorCode code) {
        return Result.err(AppError.of(code, "Model refused", "The model refused this segment."));
    }
}
