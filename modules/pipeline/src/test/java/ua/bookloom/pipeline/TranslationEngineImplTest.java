package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Guice;
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.document.DocumentModule;

/** Covers the public engine boundary and the runtime checks owned by the job it creates. */
class TranslationEngineImplTest {

    @TempDir
    private Path tempDir;

    // Removing the owning-module binding would make resolving the public port fail.
    @Test
    void injector_pipelineModule_resolvesTranslationEngineImpl() {
        final TranslationEngine engine =
                Guice.createInjector(new DocumentModule(), new PipelineModule()).getInstance(TranslationEngine.class);

        assertThat(engine).isInstanceOf(TranslationEngineImpl.class);
    }

    // Narrowing the language grammar would reject one of these supported BCP-47-shaped values.
    @ParameterizedTest
    @CsvSource(
            value = {"uk,NULL", "eng,en-US", "zh-Hant,sl-rozaj-biske-1994"},
            nullValues = "NULL")
    void newJob_validLanguageSubtags_returnsJob(final String targetLanguage, @Nullable final String sourceLanguage) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Hello.");

        final Result<TranslationJob> result = engine().newJob(
                        new TranslationRequest(
                                source, tempDir.resolve("Book.uk.md"), targetLanguage, sourceLanguage, false),
                        new ScriptedChatModel());

        assertThat(result.isOk()).isTrue();
    }

    // Dropping target-language validation would allow path-like or malformed values into output handling.
    @ParameterizedTest
    @ValueSource(strings = {"../x", "e", "engl", "en-", "en-a", "en-abcdefghi", "en_US"})
    void newJob_invalidTargetLanguage_returnsValidationWithoutModelCall(final String targetLanguage) {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result =
                engine().newJob(request("Book.md", "Book.uk.md", targetLanguage, null), model);

        assertError(
                result,
                ErrorCode.validation,
                "This language code is not valid",
                "Use two or three letters followed only by optional hyphenated language subtags.");
        assertThat(model.requests()).isEmpty();
    }

    // Applying the language grammar only to the target would accept a malformed supplied source language.
    @ParameterizedTest
    @ValueSource(strings = {"../en", "e", "english", "en-", "en_uk"})
    void newJob_invalidSourceLanguage_returnsValidationWithoutModelCall(final String sourceLanguage) {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result =
                engine().newJob(request("Book.md", "Book.uk.md", "uk", sourceLanguage), model);

        assertError(
                result,
                ErrorCode.validation,
                "This language code is not valid",
                "Use two or three letters followed only by optional hyphenated language subtags.");
        assertThat(model.requests()).isEmpty();
    }

    // Comparing raw path spellings would miss a source alias containing dot segments.
    @Test
    void newJob_normalizedSourceAlias_returnsValidationWithoutModelCall() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Original.");
        final Path alias = tempDir.resolve("folder").resolve("..").resolve("Book.md");
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result =
                engine().newJob(new TranslationRequest(source, alias, "uk", null, true), model);

        assertSameFileValidation(result);
        assertThat(model.requests()).isEmpty();
        assertThat(read(source)).isEqualTo("Original.");
    }

    // Normalizing before filesystem resolution would collapse "link/.." in the wrong order and miss this alias.
    @Test
    void newJob_symlinkParentTraversalAlias_returnsValidationWithoutModelCall() throws Exception {
        final Path actual = Files.createDirectories(tempDir.resolve("actual"));
        final Path sourceFile = TestBooks.markdown(actual.resolve("Book.md"), "Original.");
        final Path child = Files.createDirectories(actual.resolve("child"));
        final Path link = Files.createSymbolicLink(tempDir.resolve("link"), child);
        final Path source = link.resolve("..").resolve("Book.md");
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result =
                engine().newJob(new TranslationRequest(source, sourceFile, "uk", null, true), model);

        assertSameFileValidation(result);
        assertThat(model.requests()).isEmpty();
        assertThat(read(sourceFile)).isEqualTo("Original.");
    }

    // Comparing normalized names alone would miss two directory entries for the same inode.
    @Test
    void newJob_hardLinkSourceAlias_returnsValidationWithoutModelCall() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Original.");
        final Path alias = Files.createLink(tempDir.resolve("Alias.md"), source);
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<TranslationJob> result =
                engine().newJob(new TranslationRequest(source, alias, "uk", null, true), model);

        assertSameFileValidation(result);
        assertThat(model.requests()).isEmpty();
        assertThat(read(source)).isEqualTo("Original.");
    }

    // Opening inside newJob would turn a missing source into an engine failure instead of a runnable job.
    @Test
    void newJob_missingSource_defersDocumentOpenFailureToRun() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final Result<TranslationJob> created =
                engine().newJob(request("Missing.md", "Missing.uk.md", "uk", null), model);

        final Result<JobReport> run = dataOf(created).run();

        assertThat(run.isErr()).isTrue();
        assertThat(errorOf(run).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Swallowing an unexpected path-provider failure would misclassify it as a valid request.
    @Test
    void newJob_unexpectedFilesystemFailure_returnsInternalError() throws Exception {
        final ClosedPaths paths = closedPaths();

        final Result<TranslationJob> result = engine().newJob(
                        new TranslationRequest(paths.source(), paths.destination(), "uk", null, false),
                        new ScriptedChatModel());

        assertError(
                result,
                ErrorCode.internal,
                "This translation job could not be prepared",
                "The source and destination files could not be compared safely.");
        assertThat(errorOf(result).cause()).isInstanceOf(ClosedFileSystemException.class);
    }

    // Treating an indeterminate destination as absent would skip the identity check and create an unsafe job.
    @Test
    void newJob_destinationSymlinkCycle_returnsInternalError() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Source.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path cyclePeer = tempDir.resolve("Cycle.md");
        Files.createSymbolicLink(destination, cyclePeer.getFileName());
        Files.createSymbolicLink(cyclePeer, destination.getFileName());

        final Result<TranslationJob> result =
                newJobWithDestinationIdentityLog(new TranslationRequest(source, destination, "uk", null, false));

        assertError(
                result,
                ErrorCode.internal,
                "This translation job could not be prepared",
                "The source and destination files could not be compared safely.");
        assertThat(errorOf(result).cause()).isInstanceOf(java.nio.file.FileSystemException.class);
    }

    private Result<TranslationJob> newJobWithDestinationIdentityLog(final TranslationRequest request) {
        final Logger logger = (Logger) LoggerFactory.getLogger(TranslationEngineImpl.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Result<TranslationJob> result;
        try {
            result = engine().newJob(request, new ScriptedChatModel());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        assertThat(appender.list)
                .filteredOn(event ->
                        event.getFormattedMessage().contains("Unexpected translation request filesystem failure"))
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("check=destination-identity", "code=internal"));
        return result;
    }

    // Moving destination-existence validation into newJob would steal the runtime check from the job.
    @Test
    void run_existingDestinationWithoutOverwrite_returnsValidationAndKeepsDestination() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Source.");
        final Path destination = TestBooks.markdown(tempDir.resolve("Book.uk.md"), "Existing.");
        final ScriptedChatModel model = new ScriptedChatModel();
        final Result<TranslationJob> created =
                engine().newJob(new TranslationRequest(source, destination, "uk", null, false), model);

        final Result<JobReport> run = dataOf(created).run();

        assertThat(run.isErr()).isTrue();
        assertThat(errorOf(run).code()).isEqualTo(ErrorCode.validation);
        assertThat(read(destination)).isEqualTo("Existing.");
        assertThat(model.requests()).isEmpty();
    }

    // Opening malformed content in newJob would make creation fail instead of returning the document port's run error.
    @Test
    void run_malformedSource_returnsDocumentOpenFailureWithoutModelCall() {
        final Path source = TestBooks.txt(tempDir.resolve("Broken.epub"), "not a zip");
        final ScriptedChatModel model = new ScriptedChatModel();
        final Result<TranslationJob> created = engine().newJob(
                        new TranslationRequest(source, tempDir.resolve("Broken.uk.epub"), "uk", null, false), model);

        final Result<JobReport> run = dataOf(created).run();

        assertThat(run.isErr()).isTrue();
        assertThat(errorOf(run).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Losing the job's one-run claim would translate and export the same public job twice.
    @Test
    void run_secondInvocation_returnsValidation() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.");
        final TranslationJob job = dataOf(engine().newJob(
                        new TranslationRequest(source, tempDir.resolve("Book.uk.md"), "uk", null, false),
                        answers("ONE.")));

        final Result<JobReport> first = job.run();
        final Result<JobReport> second = job.run();

        assertThat(reportOf(first).end()).isEqualTo(JobState.COMPLETED);
        assertThat(second.isErr()).isTrue();
        assertThat(errorOf(second).code()).isEqualTo(ErrorCode.validation);
    }

    // Returning a boundary error instead of a failed report would break the public engine contract's partial result.
    @Test
    void run_modelFailureThroughPublicEntrypoint_returnsFailedReport() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final AppError unavailable = AppError.of(ErrorCode.unreachable, "Unavailable", "The model is unavailable.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(unavailable));
        final TranslationJob job =
                dataOf(engine().newJob(new TranslationRequest(source, destination, "uk", "en", false), model));

        final Result<JobReport> result = job.run();

        assertThat(result.isOk()).isTrue();
        assertThat(reportOf(result).end()).isEqualTo(JobState.FAILED);
        assertThat(Objects.requireNonNull(reportOf(result).error(), "report error")
                        .code())
                .isEqualTo(ErrorCode.unreachable);
        assertThat(Files.notExists(destination)).isTrue();
    }

    // Treating a model cancellation as failure would expose the wrong terminal state through the public engine.
    @Test
    void run_modelCancellationThroughPublicEntrypoint_returnsCancelledReport() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final AppError cancelled = AppError.of(ErrorCode.cancelled, "Cancelled", "The request was cancelled.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(cancelled));
        final TranslationJob job =
                dataOf(engine().newJob(new TranslationRequest(source, destination, "uk", "en", false), model));

        final Result<JobReport> result = job.run();

        assertThat(result.isOk()).isTrue();
        assertThat(reportOf(result).end()).isEqualTo(JobState.CANCELLED);
        assertThat(reportOf(result).error()).isNull();
        assertThat(Files.notExists(destination)).isTrue();
    }

    private TranslationEngine engine() {
        return new TranslationEngineImpl(documents());
    }

    private DocumentPort documents() {
        return Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    private TranslationRequest request(
            final String sourceName,
            final String destinationName,
            final String targetLanguage,
            @Nullable final String sourceLanguage) {
        return new TranslationRequest(
                tempDir.resolve(sourceName), tempDir.resolve(destinationName), targetLanguage, sourceLanguage, false);
    }

    private ClosedPaths closedPaths() throws Exception {
        final Path archive = tempDir.resolve("paths.zip");
        final URI uri = URI.create("jar:" + archive.toUri());
        final Path source;
        final Path destination;
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            source = Files.writeString(fileSystem.getPath("/Book.md"), "Source.");
            destination = Files.writeString(fileSystem.getPath("/Book.uk.md"), "Destination.");
        }
        return new ClosedPaths(source, destination);
    }

    private static ScriptedChatModel answers(final String reply) {
        return new ScriptedChatModel().answer(Result.ok(new ChatResponse(reply, FinishReason.STOP)));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (Exception cause) {
            throw new AssertionError("could not read test book", cause);
        }
    }

    private static void assertSameFileValidation(final Result<?> result) {
        assertError(
                result,
                ErrorCode.validation,
                "The source and destination are the same file",
                "Choose a different destination so the source book remains unchanged.");
    }

    private static void assertError(
            final Result<?> result, final ErrorCode code, final String title, final String message) {
        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result))
                .extracting(AppError::code, AppError::title, AppError::message)
                .containsExactly(code, title, message);
    }

    private static JobReport reportOf(final Result<JobReport> result) {
        return Objects.requireNonNull(result.data(), "job report");
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "result data");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }

    private record ClosedPaths(Path source, Path destination) {}
}
