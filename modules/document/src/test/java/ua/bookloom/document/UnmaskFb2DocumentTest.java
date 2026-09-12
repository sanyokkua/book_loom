package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * The {@code unmask}/write cases that need a real parsed FB2 document rather than a hand-built segment (task
 * groups 7.12's last two cases, 7.13 in full) — the write-path escaping a restored fragment gets, and the fragment
 * reparse {@link ua.bookloom.document.model.Jdom2TreeNode#replaceChildren} performs, which is where an entity
 * reference the source declared is resolved against that declaration and nothing else.
 *
 * <p>The entity-bearing fixture is built inline here rather than added to {@code FixtureCatalog}: that catalogue
 * drives the zero-edit FB2 golden, and a book carrying an internal entity subset fails it today because of the
 * deferred duplication defect task 1.3 records — masking neither causes nor catches that defect, so adding the
 * fixture there would turn the golden red for a reason outside this change's scope.
 */
class UnmaskFb2DocumentTest {

    /**
     * The content of the file an external entity points at. Deliberately low-entropy prose: the repository's
     * pre-commit secret scanner reads a high-entropy marker as a generic API key and refuses the commit, and a
     * test whose only job is "this text must not appear in the parsed document" needs no entropy at all.
     */
    private static final String EXTERNAL_FILE_CONTENT = "this external file must never be fetched";

    private static final String NBSP_DECLARING_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE FictionBook [<!ENTITY nbsp "&#160;">]>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <body><section><p>Розділ&nbsp;1</p></section></body>
            </FictionBook>
            """;

    private static final String AMPERSAND_SOURCE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <body><section><p>Smith &amp; Sons</p></section></body>
            </FictionBook>
            """;

    private static final String SIMPLE_PARAGRAPH_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <body><section><p>Hello world.</p></section></body>
            </FictionBook>
            """;

    @TempDir
    private Path tempDir;

    // WHEN the source declared an entity in its header, THEN it was expanded on read, so the translation carries
    // the character itself and no placeholder — and restoring and writing it succeed.
    @Test
    void write_restoredNonBreakingSpaceEntity_operationSucceeds() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, NBSP_DECLARING_XML);
        final Segment segment = opened.units().get(0).segments().get(0);

        final Result<String> restored = service.unmask(BookFormat.FB2, segment, "Глава\u00A01");

        assertThat(restored.isOk()).isTrue();
        final Document withTarget = withTarget(opened, Objects.requireNonNull(restored.data(), "data"));
        final Result<Path> written = service.write(withTarget, tempDir.resolve("out.fb2"), "uk");
        assertThat(written.isOk()).isTrue();
    }

    // WHEN a translated paragraph carries a non-breaking space, THEN the written book carries the character — the
    // entity spelling of the source is not preserved, its meaning is.
    @Test
    void write_restoredNonBreakingSpaceEntity_writtenOutputCarriesTheCharacter() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, NBSP_DECLARING_XML);
        final Segment segment = opened.units().get(0).segments().get(0);
        final Result<String> restored = service.unmask(BookFormat.FB2, segment, "Глава\u00A01");
        final Document withTarget = withTarget(opened, Objects.requireNonNull(restored.data(), "data"));

        final Result<Path> written = service.write(withTarget, tempDir.resolve("out.fb2"), "uk");

        final String xml = readUtf8(Objects.requireNonNull(written.data(), "data"));
        assertThat(xml).contains("Глава\u00A01").doesNotContain("&nbsp;");
    }

    // WHEN a restored fragment carries an entity reference the source declared, the
    // system SHALL parse it with that declaration in scope and SHALL NOT resolve any entity declared outside the
    // document.
    @Test
    void write_targetTextWithAnUndeclaredEntity_returnsValidationError() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, NBSP_DECLARING_XML);
        final Document withTarget = withTarget(opened, "a&mdash;b");

        final Result<Path> written = service.write(withTarget, tempDir.resolve("out.fb2"), "uk");

        assertThat(written.isErr()).isTrue();
        assertThat(errorOf(written).code()).isEqualTo(ErrorCode.validation);
    }

    // WHEN a restored fragment carries an entity reference the source declared, the
    // system SHALL parse it with that declaration in scope and SHALL NOT resolve any entity declared outside the
    // document.
    @Test
    void open_externallyDeclaredEntity_contributesNoFileContentToTheParsedDocument() {
        final DocumentService service = DocumentServices.newService();
        final Path secretFile = writeUtf8(tempDir.resolve("secret.txt"), EXTERNAL_FILE_CONTENT);

        final Document opened = open(service, externalEntityXml(secretFile));

        final Segment segment = opened.units().get(0).segments().get(0);
        assertThat(segment.sourceInner()).doesNotContain(EXTERNAL_FILE_CONTENT);
        assertThat(segment.masked()).doesNotContain(EXTERNAL_FILE_CONTENT);
        assertThat(segment.placeholders().values()).noneMatch(fragment -> fragment.contains(EXTERNAL_FILE_CONTENT));
    }

    // IF a target contains a character significant in its format's markup, THEN the
    // restored content SHALL carry it as escaped character data and the operation SHALL NOT fail.
    @Test
    void write_restoredAmpersandTarget_writtenParagraphTextReadsThePlainAmpersand() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, AMPERSAND_SOURCE_XML);
        final Segment segment = opened.units().get(0).segments().get(0);
        final Result<String> restored = service.unmask(BookFormat.FB2, segment, "Сміт & Сини");
        final Document withTarget = withTarget(opened, Objects.requireNonNull(restored.data(), "data"));

        final Result<Path> written = service.write(withTarget, tempDir.resolve("out.fb2"), "uk");

        final Document reopened = reopen(service, Objects.requireNonNull(written.data(), "data"));
        assertThat(reopened.units().get(0).segments().get(0).masked()).isEqualTo("Сміт & Сини");
    }

    // IF a target contains a character significant in its format's markup, THEN the
    // restored content SHALL carry it as escaped character data and the operation SHALL NOT fail.
    @Test
    void write_markupWrittenDirectlyIntoTarget_isStillWrittenAsMarkup() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, SIMPLE_PARAGRAPH_XML);
        final Document withTarget = withTarget(opened, "Привіт <emphasis>світ</emphasis>.");

        final Result<Path> written = service.write(withTarget, tempDir.resolve("out.fb2"), "uk");

        final String xml = readUtf8(Objects.requireNonNull(written.data(), "data"));
        assertThat(xml).contains("<emphasis>світ</emphasis>").doesNotContain("&lt;emphasis&gt;");
    }

    /** A FictionBook declaring an entity that points at {@code secretFile} via a {@code file:} URI, unresolved. */
    private static String externalEntityXml(Path secretFile) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE FictionBook [<!ENTITY x SYSTEM \"" + secretFile.toUri() + "\">]>\n"
                + "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n"
                + "  <body><section><p>A&x;B</p></section></body>\n"
                + "</FictionBook>\n";
    }

    private Document open(DocumentService service, String xml) {
        final Result<Document> opened = service.open(writeUtf8(tempDir.resolve("book.fb2"), xml));
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "data");
    }

    private Document reopen(DocumentService service, Path path) {
        final Result<Document> opened = service.open(path);
        assertThat(opened.isOk())
                .withFailMessage("output did not reopen: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "data");
    }

    // WHEN a restored fragment carries an entity reference the source declared, the
    // system SHALL parse it with that declaration in scope and SHALL NOT resolve any entity declared outside the
    // document.
    // The wrapper builder has three arms and the other two are exercised above and by every other FB2 test: a
    // document with a doctype carrying an internal subset, and a document with no doctype at all. This is the
    // third — a doctype whose internal subset is empty — which must produce no declaration rather than an empty
    // one, because `<!DOCTYPE bookloom-fragment []>` is not what a parser expects to be handed.
    @Test
    void unmask_documentWithADoctypeButNoInternalSubset_restoresWithoutFailing() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE FictionBook>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <body><section><p>Він відчинив <emphasis>старі</emphasis> двері.</p></section></body>
                </FictionBook>
                """);
        final Segment segment = opened.units().get(0).segments().get(0);

        final Result<String> restored = service.unmask(BookFormat.FB2, segment, "Він відчинив ⟦g0⟧нові⟦g1⟧ двері.");

        assertThat(restored.isOk()).isTrue();
        final Document withTarget = withTarget(opened, Objects.requireNonNull(restored.data(), "data"));
        assertThat(service.write(withTarget, tempDir.resolve("no-subset.fb2"), "uk")
                        .isOk())
                .isTrue();
    }

    private static Path writeUtf8(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static String readUtf8(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Rebuilds {@code document} with its sole segment carrying {@code targetInner}, leaving everything else alone. */
    private static Document withTarget(Document document, String targetInner) {
        final List<Unit> units = new ArrayList<>(document.units());
        final Unit unit = units.get(0);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        segments.set(0, withTargetInner(segments.get(0), targetInner));
        units.set(0, new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments));
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

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
