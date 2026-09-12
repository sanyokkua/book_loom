package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * The two FB2/JDOM2 findings the second audit turned up: a dropped default-namespace <em>undeclaration</em>, and
 * foreign content walked into and handed to the model as prose.
 *
 * <p>An element written {@code xmlns=""} is one the source deliberately took back out of the enclosing default
 * namespace. The composed opening tag skipped every empty-URI namespace, so {@code <foo xmlns="" bar="1">} was
 * captured as {@code <foo bar="1">} — and the wrapper a fragment is re-parsed in carries the FictionBook default
 * namespace, so the restored element silently moved <em>into</em> it and changed identity. The shipped identity
 * sweep could not see it: its comparator ignores namespace declarations by design, and a default namespace has no
 * prefix to compare.
 *
 * <p>Inline MathML and SVG were XHTML-only atomic, so an FB2 book's inline {@code <m:math>} was walked into and its
 * mathematical identifier sent out as translatable text — against DD-49, and inconsistent with the walker's own
 * block-level exclusion, which already treats {@code math} format-agnostically.
 */
class Fb2ForeignContentMaskingTest {

    private static final String NAMESPACE_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>S</book-title><lang>uk</lang></title-info></description>
              <body><section>
                <p>Ось <foo xmlns="" bar="1">текст</foo> кінець.</p>
                <p>Далі <emphasis>слово</emphasis> кінець.</p>
              </section></body>
            </FictionBook>
            """;

    private static final String FOREIGN_CONTENT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:m="http://www.w3.org/1998/Math/MathML">
              <description><title-info><book-title>S</book-title><lang>uk</lang></title-info></description>
              <body><section>
                <p>Формула <m:math><m:mi>alpha</m:mi></m:math> тут.</p>
                <p>Малюнок <svg xmlns="http://www.w3.org/2000/svg"><text>Fig 1</text></svg> тут.</p>
              </section></body>
            </FictionBook>
            """;

    @TempDir
    private Path tempDir;

    // WHEN an element inside a segment's content has at least one child node and is not a
    // protected span, the system SHALL emit two tokens for it, one carrying its opening tag with every attribute
    // and namespace declaration the format's parser records for it.
    @Test
    void open_elementCarryingADefaultNamespaceUndeclaration_capturesItInTheOpeningToken() {
        final Segment segment = segmentsOf(NAMESPACE_XML).get(0);

        assertThat(segment.placeholders()).containsEntry("g0", "<foo xmlns=\"\" bar=\"1\">");
    }

    // WHEN an element inside a segment's content has at least one child node and is not a
    // protected span, the system SHALL emit two tokens for it, one carrying its opening tag with every attribute
    // and namespace declaration the format's parser records for it.
    @Test
    void write_elementCarryingADefaultNamespaceUndeclaration_stillCarriesItAfterAnIdentityRestore() {
        final String written = identityRestoreAndWrite(NAMESPACE_XML, "undeclared.fb2");

        assertThat(written).contains("<foo xmlns=\"\" bar=\"1\">текст</foo>");
    }

    // WHEN an element inside a segment's content has at least one child node and is not a
    // protected span, the system SHALL emit two tokens for it, one carrying its opening tag with every attribute
    // and namespace declaration the format's parser records for it.
    // The guard in the other direction, which is what keeps the fix from being "always emit xmlns=": an element
    // that was never undeclared must not gain the undeclaration and so move OUT of the FictionBook namespace.
    @Test
    void write_ordinaryElementInTheDefaultNamespace_gainsNoUndeclaration() {
        final Segment segment = segmentsOf(NAMESPACE_XML).get(1);
        final String written = identityRestoreAndWrite(NAMESPACE_XML, "ordinary.fb2");

        assertThat(segment.placeholders()).containsEntry("g0", "<emphasis>");
        assertThat(written).contains("<emphasis>слово</emphasis>");
        assertThat(written).doesNotContain("<emphasis xmlns=\"\"");
    }

    // WHEN a node inside a segment's content is a protected span — inline MathML or an inline
    // vector-graphic element — the system SHALL emit exactly one token for it and SHALL NOT expose any part of its
    // interior in the masked form.
    @ParameterizedTest
    @CsvSource({"0,Формула ⟦g0⟧ тут.,alpha", "1,Малюнок ⟦g0⟧ тут.,Fig 1"})
    void open_fb2InlineForeignContent_isOneAtomicTokenHidingItsInterior(int order, String masked, String interior) {
        final Segment segment = segmentsOf(FOREIGN_CONTENT_XML).get(order);

        assertThat(segment.masked()).isEqualTo(masked);
        assertThat(segment.placeholders()).hasSize(1);
        assertThat(segment.masked()).doesNotContain(interior);
    }

    private List<Segment> segmentsOf(String xml) {
        return segmentsOf(open(DocumentServices.newService(), xml, "probe.fb2"));
    }

    private Document open(DocumentService service, String xml, String fileName) {
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve(fileName), xml, StandardCharsets.UTF_8);
        final Result<Document> opened = service.open(file);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "document");
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    /** Opens, restores every segment from its own masked form, writes, and returns the written document's text. */
    private String identityRestoreAndWrite(String xml, String outputName) {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, xml, "source-" + outputName);
        final List<String> targets = segmentsOf(opened).stream()
                .map(segment -> identityRestore(service, segment))
                .toList();

        final Result<Path> written =
                service.write(SegmentTargets.withTargets(opened, targets), tempDir.resolve(outputName), "uk");

        assertThat(written.isOk())
                .withFailMessage("write failed: %s", written.error())
                .isTrue();
        return readUtf8(Objects.requireNonNull(written.data(), "data"));
    }

    private static String identityRestore(DocumentService service, Segment segment) {
        final Result<String> restored = service.unmask(BookFormat.FB2, segment, segment.masked());
        assertThat(restored.isOk())
                .withFailMessage("identity restore failed: %s", restored.error())
                .isTrue();
        return Objects.requireNonNull(restored.data(), "data");
    }

    private static String readUtf8(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
