package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.RefusalFixtures;

/**
 * Drives each of the four refusal fixtures (task 5.2) through {@link DocumentService#open}, independently of
 * {@code DocumentServiceTest}'s own ad-hoc failure-path fixtures (task group 4) — a second proof against the
 * richer named fixture family design.md D6 calls for.
 */
class EpubRefusalFixturesTest {

    @TempDir
    private Path tempDir;

    // Covers: EC-EPUB-1 — content encryption in META-INF/encryption.xml refuses the whole book: ErrorCode.validation
    // with no Document returned.
    @Test
    void open_contentEncryptedFixture_returnsValidationErrorWithNoDocument() {
        final Path epub = RefusalFixtures.contentEncrypted(tempDir.resolve("content-encrypted.epub"));

        final Result<Document> result = newService().open(epub);

        assertThat(result.isErr()).isTrue();
        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // Covers: FR-DOC-EPUB-7 — an EPUB whose encryption.xml declares only the known IDPF font-obfuscation algorithm
    // is processed normally rather than refused.
    @Test
    void open_fontObfuscationOnlyFixture_returnsOkWithAtLeastOneSegment() {
        final Path epub = RefusalFixtures.fontObfuscationOnly(tempDir.resolve("font-obfuscation.epub"));

        final Result<Document> result = newService().open(epub);

        assertThat(result.isOk()).isTrue();
        final Document document = Objects.requireNonNull(result.data(), "data");
        assertThat(document.units()).isNotEmpty();
        assertThat(document.units().get(0).segments()).isNotEmpty();
    }

    // Covers: EC-EPUB-2 — an archive whose container.xml points at an OPF path the archive does not contain is a
    // validation failure.
    @Test
    void open_missingOpfFixture_returnsValidationError() {
        final Path epub = RefusalFixtures.missingOpf(tempDir.resolve("missing-opf.epub"));

        final Result<Document> result = newService().open(epub);

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // Covers: EC-EPUB-2 — a file that is not a zip archive at all is a validation failure.
    @Test
    void open_notAZipFixture_returnsValidationError() {
        final Path notAZip = RefusalFixtures.notAZip(tempDir.resolve("book.epub"));

        final Result<Document> result = newService().open(notAZip);

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
