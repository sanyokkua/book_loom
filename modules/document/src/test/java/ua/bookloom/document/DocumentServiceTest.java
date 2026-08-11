package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * {@link DocumentService}'s failure-path classification at the {@link ua.bookloom.api.document.DocumentPort}
 * boundary (task group 4), against tiny ad-hoc EPUB fixtures built inline — the richer fixture family lands with
 * task group 5.
 */
class DocumentServiceTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String OPF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Book</dc:title>
                <dc:creator>A. Author</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="c01"/>
              </spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter One</title></head>
            <body><p>Prose.</p></body>
            </html>
            """;

    private static final String ENCRYPTION_XML_CONTENT_ENCRYPTED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <enc:EncryptedData xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>
              </enc:EncryptedData>
            </encryption>
            """;

    @TempDir
    private Path tempDir;

    // Covers: EC-EPUB-2 — a file that is not a zip archive at all returns ErrorCode.validation with no partial
    // import.
    @Test
    void open_nonZipFile_returnsValidationErrorWithNoPartialDocument() {
        final Path notAZip = tempDir.resolve("book.epub");
        writeBytes(notAZip, "this is not a zip file at all".getBytes(StandardCharsets.UTF_8));

        final Result<Document> result = newService().open(notAZip);

        assertThat(result.isErr()).isTrue();
        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // Covers: FR-DOC-EPUB-7 — content encryption (or an unrecognised algorithm) refuses the whole book, with a
    // title/message distinguishable from a corrupt-file failure.
    @Test
    void open_drmProtectedBook_returnsValidationErrorDistinctFromCorruptFile() {
        final Path drmBook = tempDir.resolve("drm.epub");
        zip(
                drmBook,
                entries(
                        "META-INF/container.xml", CONTAINER_XML,
                        "META-INF/encryption.xml", ENCRYPTION_XML_CONTENT_ENCRYPTED,
                        "OEBPS/content.opf", OPF,
                        "OEBPS/c01.xhtml", CHAPTER));
        final Path corruptBook = tempDir.resolve("corrupt.epub");
        writeBytes(corruptBook, "not a zip".getBytes(StandardCharsets.UTF_8));
        final DocumentService service = newService();

        final Result<Document> drmResult = service.open(drmBook);
        final Result<Document> corruptResult = service.open(corruptBook);

        assertThat(drmResult.isErr()).isTrue();
        assertThat(drmResult.data()).isNull();
        final AppError drmError = errorOf(drmResult);
        final AppError corruptError = errorOf(corruptResult);
        assertThat(drmError.code()).isEqualTo(ErrorCode.validation);
        assertThat(drmError.title()).isNotEqualTo(corruptError.title());
        assertThat(drmError.message()).isNotEqualTo(corruptError.message());
    }

    // Covers: DD-14 — a document failure's details never carries the source file's path; the original exception
    // is available on cause instead.
    @Test
    void open_parseFailure_carriesNoFilesystemPathInDetails() {
        final Path corruptBook = tempDir.resolve("book-with-a-telling-path.epub");
        writeBytes(corruptBook, "not a zip".getBytes(StandardCharsets.UTF_8));

        final Result<Document> result = newService().open(corruptBook);

        final AppError error = errorOf(result);
        assertThat(error.details()).isNull();
        assertThat(error.cause()).isNotNull();
    }

    // Suppressed deliberately: NullAway forbids this call statically, which is exactly why the runtime
    // requireNonNull guard has to exist and be proven (same rationale as ResultTest#ok_null_isRejected).
    @SuppressWarnings("NullAway")
    @Test
    void open_nullSource_throwsNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> newService().open(null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void write_nullDocument_throwsNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> newService().write(null, tempDir.resolve("out.epub"), "uk"));
    }

    // Covers: DD-14 — WHEN parsing fails with an unforeseen runtime exception, THEN the caller receives a failed
    // result carrying ErrorCode.internal, the original exception is available on cause, and no exception
    // propagates out of the port method. write() exercises this through DocumentNotOpenException, the boundary's
    // other ErrorCode.internal producer alongside the catch-all Throwable clause.
    @Test
    void write_documentIdNeverOpened_returnsInternalErrorWithCause() {
        final Document neverOpened = new Document(
                "never-opened-id", BookFormat.EPUB, null, null, null, null, "deadbeef", Map.of(), List.of());

        final Result<Path> result = newService().write(neverOpened, tempDir.resolve("out.epub"), "uk");

        assertThat(result.isErr()).isTrue();
        assertThat(result.data()).isNull();
        final AppError error = errorOf(result);
        assertThat(error.code()).isEqualTo(ErrorCode.internal);
        assertThat(error.cause()).isNotNull();
    }

    // Covers: FR-DOC-03 — IF target text supplied for a segment cannot be parsed as FB2's inline markup — a bare
    // `&` that does not open a valid entity — THEN the caller receives a failed result carrying
    // ErrorCode.validation, and no exception propagates out of the port method.
    @Test
    void write_fb2TargetTextWithBareAmpersand_returnsValidationErrorWithNoException() {
        final DocumentService service = newService();
        final Document opened = openFb2Fixture(service);
        final Document withMalformedTarget = withTarget(opened, 0, 0, "A & B");
        final Path destination = tempDir.resolve("out-ampersand.fb2");
        final AtomicReference<Result<Path>> written = new AtomicReference<>();

        assertThatCode(() -> written.set(service.write(withMalformedTarget, destination, "uk")))
                .doesNotThrowAnyException();

        final Result<Path> result = Objects.requireNonNull(written.get(), "written");
        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // Covers: FR-DOC-03 — IF target text supplied for a segment cannot be parsed as FB2's inline markup — a bare
    // `<` that does not open a valid element — THEN the caller receives a failed result carrying
    // ErrorCode.validation.
    @Test
    void write_fb2TargetTextWithBareLessThan_returnsValidationError() {
        final DocumentService service = newService();
        final Document opened = openFb2Fixture(service);
        final Document withMalformedTarget = withTarget(opened, 0, 0, "x < y");
        final Path destination = tempDir.resolve("out-less-than.fb2");

        final Result<Path> written = service.write(withMalformedTarget, destination, "uk");

        assertThat(written.isErr()).isTrue();
        assertThat(errorOf(written).code()).isEqualTo(ErrorCode.validation);
    }

    // Covers: FR-DOC-03 — an unrelated failure (a destination whose parent directory does not exist) during an
    // FB2 write whose every segment carries well-formed target text is still classified ErrorCode.internal, and
    // no exception propagates out of the port method — proving the fix narrows classification rather than
    // reclassifying every write failure as validation.
    @Test
    void write_fb2WellFormedTargetsToMissingParentDirectory_returnsInternalErrorWithNoException() {
        final DocumentService service = newService();
        final Document opened = openFb2Fixture(service);
        final Document withWellFormedTargets = withEverySegmentTargetSetToItsSourceText(opened);
        final Path destination = tempDir.resolve("does-not-exist").resolve("out.fb2");
        final AtomicReference<Result<Path>> written = new AtomicReference<>();

        assertThatCode(() -> written.set(service.write(withWellFormedTargets, destination, "uk")))
                .doesNotThrowAnyException();

        final Result<Path> result = Objects.requireNonNull(written.get(), "written");
        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
    }

    private Document openFb2Fixture(DocumentService service) {
        final Path source = Fb2Fixtures.primary(tempDir.resolve("book.fb2"));
        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("FB2 fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "data");
    }

    /** Rebuilds {@code document} with one segment carrying target text, leaving every other component alone. */
    private static Document withTarget(Document document, int unitIndex, int segmentOrder, String targetInner) {
        final List<Unit> units = new ArrayList<>(document.units());
        final Unit unit = units.get(unitIndex);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        segments.set(segmentOrder, withTargetInner(segments.get(segmentOrder), targetInner));
        units.set(
                unitIndex, new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments));
        return withUnits(document, units);
    }

    /** Rebuilds {@code document} with every segment's target text set to its own well-formed source text. */
    private static Document withEverySegmentTargetSetToItsSourceText(Document document) {
        final List<Unit> units = new ArrayList<>();
        for (final Unit unit : document.units()) {
            final List<Segment> segments = new ArrayList<>();
            for (final Segment segment : unit.segments()) {
                segments.add(withTargetInner(segment, segment.sourceInner()));
            }
            units.add(new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments));
        }
        return withUnits(document, units);
    }

    private static Document withUnits(Document document, List<Unit> units) {
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                units);
    }

    private static Segment withTargetInner(Segment segment, String targetInner) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                targetInner,
                segment.status(),
                segment.confidence());
    }

    @Test
    void open_validFixture_returnsOkWithParsedDocument() {
        final Path epub = tempDir.resolve("book.epub");
        zip(
                epub,
                entries(
                        "mimetype", "application/epub+zip",
                        "META-INF/container.xml", CONTAINER_XML,
                        "OEBPS/content.opf", OPF,
                        "OEBPS/c01.xhtml", CHAPTER));

        final Result<Document> result = newService().open(epub);

        assertThat(result.isOk()).isTrue();
        final Document document = Objects.requireNonNull(result.data(), "data");
        assertThat(document.units()).hasSize(1);
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }

    private static Map<String, String> entries(String... namesAndContents) {
        final Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            entries.put(namesAndContents[i], namesAndContents[i + 1]);
        }
        return entries;
    }

    private static void zip(Path file, Map<String, String> entries) {
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeBytes(Path file, byte[] content) {
        try {
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
