package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TestBooks;

/** The destination checks {@code ExportServiceImpl.newExport} runs on the stored project's source before a job exists. */
class ExportServiceDestinationTest {

    @TempDir
    private Path tempDir;

    private ExportServiceImpl service;
    private ProjectRepository projects;

    @BeforeEach
    void setUp() {
        final var injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(ExportServiceImpl.class);
        projects = injector.getInstance(ProjectRepository.class);
    }

    // Omitting either suffix check or the same-format comparison would accept one of these invalid pairs.
    @ParameterizedTest
    @CsvSource({
        "Book.pdf,Book.uk.pdf",
        "Book.md,Book.uk.pdf",
        "Book.md,Book.uk.txt",
        "Book.fb2.zip,Book.uk.fb2",
        "Book.fb2,Book.uk.fb2.zip",
        "BOOK.FB2.ZIP,Book.uk.fb2"
    })
    void newExport_unsupportedOrMismatchedFormats_returnsValidationBeforeAnyWrite(
            final String sourceName, final String destinationName) {
        storeProject(tempDir.resolve(sourceName));

        final Result<ExportJob> result = service.newExport(request(tempDir.resolve(destinationName), true), null);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(tempDir.resolve(destinationName)).doesNotExist();
    }

    // Treating every suffix difference as a new file type would refuse two spellings of one Markdown type.
    @ParameterizedTest
    @CsvSource({"Book.markdown,Book.uk.md", "Book.FB2.ZIP,Book.uk.fb2.zip"})
    void newExport_sameFileTypeWithDifferentSuffixSpelling_returnsJob(
            final String sourceName, final String destinationName) {
        storeProject(tempDir.resolve(sourceName));

        final Result<ExportJob> result = service.newExport(request(tempDir.resolve(destinationName), true), null);

        assertThat(result.isOk()).isTrue();
    }

    // Comparing raw path spellings would miss a destination containing dot segments.
    @Test
    void newExport_normalizedSourceAlias_returnsValidationAndKeepsTheSource() throws IOException {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Original.");
        storeProject(source);
        final Path alias = tempDir.resolve("folder").resolve("..").resolve("Book.md");

        final Result<ExportJob> result = service.newExport(request(alias, true), null);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("Original.");
    }

    // Normalizing before filesystem resolution would collapse "link/.." in the wrong order and miss this alias.
    @Test
    void newExport_symlinkParentTraversalAlias_returnsValidationAndKeepsTheSource() throws IOException {
        final Path actual = Files.createDirectories(tempDir.resolve("actual"));
        final Path sourceFile = TestBooks.markdown(actual.resolve("Book.md"), "Original.");
        final Path child = Files.createDirectories(actual.resolve("child"));
        final Path link = Files.createSymbolicLink(tempDir.resolve("link"), child);
        storeProject(link.resolve("..").resolve("Book.md"));

        final Result<ExportJob> result = service.newExport(request(sourceFile, true), null);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(sourceFile)).isEqualTo("Original.");
    }

    // Comparing normalized names alone would miss two directory entries for the same inode.
    @Test
    void newExport_hardLinkSourceAlias_returnsValidationAndKeepsTheSource() throws IOException {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Original.");
        final Path alias = Files.createLink(tempDir.resolve("Alias.md"), source);
        storeProject(source);

        final Result<ExportJob> result = service.newExport(request(alias, true), null);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("Original.");
    }

    // Treating an indeterminate destination as absent would skip the identity check and create an unsafe job.
    @Test
    void newExport_destinationSymlinkCycle_returnsInternal() throws IOException {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Source.");
        storeProject(source);
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path cyclePeer = tempDir.resolve("Cycle.md");
        Files.createSymbolicLink(destination, cyclePeer.getFileName());
        Files.createSymbolicLink(cyclePeer, destination.getFileName());

        final Result<ExportJob> result = service.newExport(request(destination, true), null);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
    }

    @Test
    void newExport_unknownProject_returnsValidation() {
        final Result<ExportJob> result = service.newExport(request(tempDir.resolve("Book.uk.md"), true), null);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    private void storeProject(final Path source) {
        projects.save(new Project("p1", source, BookFormat.MARKDOWN, "hash", BookBrief.defaults("en")));
    }

    private static ExportRequest request(final Path destination, final boolean overwrite) {
        return new ExportRequest("p1", destination, overwrite, Set.of(), false);
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }
}
