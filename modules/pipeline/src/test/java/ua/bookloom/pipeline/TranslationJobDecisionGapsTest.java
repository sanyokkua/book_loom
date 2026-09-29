package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
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
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;

/** Proves, through a whole job, the decisions the scenario audit found covered only at the translator. */
class TranslationJobDecisionGapsTest {

    @TempDir
    private Path tempDir;

    // Reading the book's declaration instead of the brief would send the declared en instead of the chosen de.
    @Test
    void run_briefSourceLanguage_beatsTheDeclaredLanguage() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.", "en");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.");

        final JobReport report = report(job(UnaryOperator.identity(), source, "de", model));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains("from German (de) into Ukrainian (uk)");
    }

    // The brief's source language is named as it stands, and none is named when the brief has none.
    @ParameterizedTest(name = "brief={0}")
    @CsvSource(
            delimiter = '|',
            nullValues = "NULL",
            value = {
                "en|from English (en) into Ukrainian (uk)",
                "NULL|from the language of this segment (infer it from its text) into Ukrainian (uk)"
            })
    void run_briefSourceLanguage_isNamedInThePrompt(
            @Nullable final String briefSource, final String expectedInstruction) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.", "en");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("ONE.");

        final JobReport report = report(job(UnaryOperator.identity(), source, briefSource, model));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests().getFirst().messages().getFirst().content()).contains(expectedInstruction);
    }

    // A flag that stopped the loop would leave the second paragraph unsent.
    @ParameterizedTest(name = "{0}")
    @MethodSource("flaggingReplies")
    void run_flaggedFirstSegment_stillSendsAndAcceptsTheSecond(
            final String label,
            final Result<ChatResponse> firstReply,
            final int firstSegmentCalls,
            final ErrorCode reason) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.\n\nShe left.\n");
        final ScriptedChatModel model = new ScriptedChatModel();
        IntStream.range(0, firstSegmentCalls).forEach(call -> model.answer(firstReply));
        model.answer(
                Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply("SHE LEFT."), FinishReason.STOP)));

        final JobReport report = report(job(UnaryOperator.identity(), source, "en", model));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.accepted()).isEqualTo(1);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.md:0", reason));
        assertThat(model.requests()).hasSize(firstSegmentCalls + 1);
    }

    // A missing token is flagged only after the draft, its placeholder repair and the one directed fix of Fast; the
    // other replies flag the segment at once, after its one call.
    private static Stream<Arguments> flaggingReplies() {
        return Stream.of(
                Arguments.of(
                        "missing token",
                        reply("HE OPENED THE ⟦g0⟧OLD DOOR.", FinishReason.STOP),
                        3,
                        ErrorCode.validation),
                Arguments.of(
                        "whitespace with a normal finish",
                        reply("  \n", FinishReason.STOP),
                        1,
                        ErrorCode.emptyCompletion),
                Arguments.of("cut off by length", reply("HE OPENED", FinishReason.LENGTH), 1, ErrorCode.validation),
                Arguments.of("model emptyCompletion", refusal(ErrorCode.emptyCompletion), 1, ErrorCode.emptyCompletion),
                Arguments.of("model contextWindow", refusal(ErrorCode.contextWindow), 1, ErrorCode.contextWindow));
    }

    // Reporting a thrown model call under any other code would hide an unexpected fault behind a known outcome.
    @Test
    void run_modelCallThrows_endsFailedWithInternalError() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.\n");
        final ScriptedChatModel model = new ScriptedChatModel().throwFailure(new IllegalStateException("model broke"));

        final JobReport report = report(job(UnaryOperator.identity(), source, "en", model));

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
        final UnaryOperator<DocumentPort> port = documents -> new TestDocuments.ForwardingPort(documents) {
            @Override
            public Result<String> unmask(final BookFormat format, final Segment segment, final String translated) {
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

    private TranslationJobImpl job(
            final UnaryOperator<DocumentPort> decorate,
            final Path source,
            @Nullable final String sourceLanguage,
            final ChatModel model) {
        return TranslationJobTestSupport.job(
                TranslationJobTestSupport.project(
                        decorate, source, TranslationJobTestSupport.brief(sourceLanguage, "uk")),
                model);
    }

    private static JobReport report(final TranslationJobImpl job) {
        return TranslationJobTestSupport.report(job.run());
    }

    private static Result<ChatResponse> reply(final String content, final FinishReason finish) {
        return Result.ok(
                new ChatResponse(content.isBlank() ? content : TranslationJobTestSupport.targetReply(content), finish));
    }

    private static Result<ChatResponse> refusal(final ErrorCode code) {
        return Result.err(AppError.of(code, "Model refused", "The model refused this segment."));
    }
}
