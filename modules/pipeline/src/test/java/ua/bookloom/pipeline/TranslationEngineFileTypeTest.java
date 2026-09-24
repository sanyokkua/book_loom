package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.document.DocumentModule;

/** Covers the request checks that compare the source's and the destination's file types before any model call. */
class TranslationEngineFileTypeTest {

    @TempDir
    private Path tempDir;

    // Omitting either suffix check or the same-format comparison would accept one of these invalid pairs.
    @ParameterizedTest
    @CsvSource({"Book.pdf,Book.uk.pdf", "Book.md,Book.uk.pdf", "Book.md,Book.uk.txt"})
    void newJob_unsupportedOrMismatchedFormats_returnsValidationWithoutModelCall(
            final String sourceName, final String destinationName) {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result = newJob(sourceName, destinationName, model);

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Comparing only the book format would accept a change of FB2 container the writer cannot honour: the job would
    // translate the whole book before the written file failed its own re-open check.
    @ParameterizedTest
    @CsvSource({"Book.fb2.zip,Book.uk.fb2", "Book.fb2,Book.uk.fb2.zip", "BOOK.FB2.ZIP,Book.uk.fb2"})
    void newJob_fb2ContainerMismatch_returnsValidationWithoutModelCall(
            final String sourceName, final String destinationName) {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result = newJob(sourceName, destinationName, model);

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result))
                .extracting(AppError::code, AppError::title, AppError::message)
                .containsExactly(
                        ErrorCode.validation,
                        "The source and destination formats differ",
                        "Choose a destination with the same file type as the source book.");
        assertThat(model.requests()).isEmpty();
    }

    // Treating every suffix difference as a new file type would refuse the two spellings of one Markdown type, or a
    // zipped FB2 container spelled in another letter case.
    @ParameterizedTest
    @CsvSource({"Book.markdown,Book.uk.md", "Book.FB2.ZIP,Book.uk.fb2.zip"})
    void newJob_sameFileTypeWithDifferentSuffixSpelling_returnsJob(
            final String sourceName, final String destinationName) {
        final Result<TranslationJob> result = newJob(sourceName, destinationName, new ScriptedChatModel());

        assertThat(result.isOk()).isTrue();
    }

    private Result<TranslationJob> newJob(
            final String sourceName, final String destinationName, final ScriptedChatModel model) {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final TranslationRequest request = new TranslationRequest(
                tempDir.resolve(sourceName), tempDir.resolve(destinationName), "uk", null, false);
        return new TranslationEngineImpl(documents, new ObjectMapper()).newJob(request, model);
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }
}
