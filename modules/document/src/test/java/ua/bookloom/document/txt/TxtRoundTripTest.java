package ua.bookloom.document.txt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.CorruptContainerException;

/**
 * The plain-text read and write paths.
 *
 * <p>Every fixture here is built from an explicit Java string rather than a committed file, for the reason
 * design.md D8 gives: this format's fixtures <em>are</em> byte sequences — a byte-order mark, CRLF endings,
 * trailing whitespace — and a committed file of that kind is one editor save or one {@code core.autocrlf} setting
 * away from being normalised into a fixture that no longer tests what it was written to test, with a
 * <em>passing</em> test as the failure mode.
 */
class TxtRoundTripTest {

    @TempDir
    private Path tempDir;

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    /** A byte-order mark, CRLF endings, a run of blank lines, four-space indentation and trailing whitespace. */
    private static final String PRIMARY = "﻿One.\r\n\r\n\r\n\r\n    Two, indented.   \r\n\r\nThree.\r\n";

    /**
     * A paragraph carrying a literal placeholder bracket pair — the one shape TXT masks at all (PlainTextMasker):
     * each of {@code ⟦} and {@code ⟧} becomes its own atomic protected-span token. {@link #PRIMARY} above carries
     * neither, so a "genuinely masked" claim proved only against it would be vacuous.
     */
    private static final String WITH_BRACKET = "One ⟦not a token⟧ two.\n";

    /**
     * Enough Cyrillic prose for a statistical detector to work with. TXT carries no declaration, so detection is
     * the only signal there is — and a detector cannot identify a code page from thirteen bytes. A fixture that
     * short would be testing the limits of detection rather than the round trip it is written to prove.
     */
    private static final String CYRILLIC_PROSE = """
            Он бегал по улице, крича во весь голос, и никто не обращал на него внимания.
            Дождь шёл третий день подряд, и город казался серым насквозь и навсегда.

            Вечером она вышла на балкон и долго смотрела, как гаснут окна напротив.
            Утром всё повторилось снова, только ветер переменился и стало холоднее.
            """;

    private final OpenTxtRegistry registry = new OpenTxtRegistry();

    private Document open(String text, Charset charset, String fileName) {
        final Path file = tempDir.resolve(fileName);
        try {
            Files.write(file, text.getBytes(charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TxtReader(registry).read(file);
    }

    private Document openPrimary() {
        return open(PRIMARY, StandardCharsets.UTF_8, "notes.txt");
    }

    private Path writeOut(Document document, String targetLanguage) {
        return new TxtWriter(registry).write(document, tempDir.resolve("out.txt"), targetLanguage);
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().get(0).segments();
    }

    // blank lines separate paragraphs, and one PARAGRAPH segment is emitted per paragraph
    // in file order.
    @Test
    void read_blankLineSeparatedParagraphs_yieldOneSegmentEachInOrder() {
        final Document document = open("One.\n\nTwo.\n\nThree.\n", StandardCharsets.UTF_8, "notes.txt");

        assertThat(segmentsOf(document)).extracting(Segment::sourceInner).containsExactly("One.", "Two.", "Three.");
        assertThat(segmentsOf(document)).extracting(Segment::kind).containsOnly(SegmentKind.PARAGRAPH);
    }

    // a run of several blank lines yields no extra segment.
    @Test
    void read_runOfSeveralBlankLines_yieldsNoExtraSegment() {
        final Document document = open("One.\r\n\r\n\r\n\r\nTwo.\r\n", StandardCharsets.UTF_8, "notes.txt");

        assertThat(segmentsOf(document)).hasSize(2);
    }

    // a plain-text file is one unit named for the file, with the plain-text media type.
    @Test
    void read_plainTextFile_yieldsOneUnitNamedForTheFile() {
        final Unit unit = openPrimary().units().get(0);

        assertThat(unit.id()).isEqualTo("notes.txt");
        assertThat(unit.href()).isEqualTo("notes.txt");
        assertThat(unit.mediaType()).isEqualTo("text/plain");
        assertThat(openPrimary().format()).isEqualTo(BookFormat.TXT);
    }

    // a byte-order mark fixes the charset, is recorded, and sits outside every span.
    @Test
    void read_byteOrderMark_isRecordedAndExcludedFromEverySpan() {
        final Document document = openPrimary();

        assertThat(document.hasBom()).isTrue();
        assertThat(document.charset()).isEqualTo("UTF-8");
        assertThat(segmentsOf(document))
                .allSatisfy(s -> assertThat(((ByteSpanAnchor) s.anchor()).startInclusive())
                        .isGreaterThanOrEqualTo(3));
    }

    // indentation stays outside the segment's span, so it is copied through untouched.
    @Test
    void read_indentedParagraph_leavesTheIndentationOutsideTheSpan() {
        assertThat(segmentsOf(openPrimary()))
                .extracting(Segment::sourceInner)
                .containsExactly("One.", "Two, indented.", "Three.");
    }

    // WHEN a file is reassembled with no segment carrying target text, THEN the output
    // bytes are identical to the source's, byte-order mark and line endings included.
    @Test
    void write_zeroEditRoundTrip_isByteIdenticalIncludingBomAndLineEndings() {
        final Path output = writeOut(openPrimary(), "uk");

        assertThat(bytesOf(output)).isEqualTo(PRIMARY.getBytes(StandardCharsets.UTF_8));
    }

    // WHEN a document is reassembled with no target text, the system SHALL produce output
    // canonical-equal to the source for EPUB, FB2 and Markdown and byte-identical for TXT.
    @Test
    void write_zeroEditRoundTripOfBracketBearingParagraph_isByteIdenticalAndGenuinelyMasked() {
        final Document document = open(WITH_BRACKET, StandardCharsets.UTF_8, "brackets.txt");

        final Path output = writeOut(document, "uk");

        assertThat(bytesOf(output)).isEqualTo(WITH_BRACKET.getBytes(StandardCharsets.UTF_8));
        assertThat(segmentsOf(document))
                .as("at least one segment was genuinely masked on the way through")
                .anySatisfy(segment -> assertThat(segment.placeholders()).isNotEmpty());
    }

    // only the translated paragraph's bytes change, and every byte before it is unchanged.
    @Test
    void write_onlyTheTranslatedParagraph_changesItsOwnBytes() {
        final Document document = withTarget(open("One.\n\nTwo.\n", StandardCharsets.UTF_8, "notes.txt"), 1, "Два.");

        final Path output = writeOut(document, "uk");

        assertThat(new String(bytesOf(output), StandardCharsets.UTF_8)).isEqualTo("One.\n\nДва.\n");
    }

    // an export passed a target language is still byte-identical, because plain text has
    // nowhere to record one and inventing a place would add content the source never had.
    @Test
    void write_withATargetLanguage_addsNothingAndStaysByteIdentical() {
        assertThat(bytesOf(writeOut(openPrimary(), "uk"))).isEqualTo(PRIMARY.getBytes(StandardCharsets.UTF_8));
    }

    // IF a segment's target text contains a character the source encoding cannot
    // represent, THEN the export fails with a validation outcome and no output file is written.
    @Test
    void write_unrepresentableTargetCharacter_failsAndWritesNoFile() {
        final Document document = withTarget(open(CYRILLIC_PROSE, WINDOWS_1251, "notes.txt"), 1, "Два 車.");
        final Path destination = tempDir.resolve("out.txt");

        assertThatThrownBy(() -> new TxtWriter(registry).write(document, destination, "uk"))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("cannot represent");
        assertThat(Files.exists(destination)).isFalse();
    }

    // a representable translation into the source's own encoding is written in it.
    @Test
    void write_representableTargetInASingleByteEncoding_isWrittenInThatEncoding() {
        final Document document = withTarget(open(CYRILLIC_PROSE, WINDOWS_1251, "notes.txt"), 1, "Три речення.");

        final Path output = writeOut(document, "uk");

        assertThat(new String(bytesOf(output), WINDOWS_1251))
                .isEqualTo(CYRILLIC_PROSE.replace(
                        "Вечером она вышла на балкон и долго смотрела, как гаснут окна напротив.\n"
                                + "Утром всё повторилось снова, только ветер переменился и стало холоднее.",
                        "Три речення."));
    }

    private static Document withTarget(Document document, int segmentOrder, String targetInner) {
        final Unit unit = document.units().get(0);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        segments.set(segmentOrder, withTargetInner(segments.get(segmentOrder), targetInner));
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                List.of(new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments)));
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
}
