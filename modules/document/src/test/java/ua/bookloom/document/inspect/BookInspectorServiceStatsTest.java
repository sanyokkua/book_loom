package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.epub.EpubZipBuilder;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.OpenMarkdownRegistry;

/**
 * {@code FormatInspection#stats} for every format (task 4.5): the segment, word, image, code-block, font,
 * verse-line, footnote, table and protected-formatting counts, computed from each format's own already-parsed
 * state.
 */
class BookInspectorServiceStatsTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final int CHAPTER_COUNT = 20;
    private static final int PARAGRAPHS_PER_CHAPTER = 62;
    private static final int TOTAL_SEGMENTS = CHAPTER_COUNT * PARAGRAPHS_PER_CHAPTER;
    private static final int BASE_WORDS_PER_PARAGRAPH = 63;
    private static final int EXTRA_WORD_PARAGRAPHS = 94;
    private static final int TOTAL_WORDS = 78214;
    private static final int IMAGE_COUNT = 7;

    @TempDir
    private Path tempDir;

    @Test
    void stats_epubFixture_countsSegmentsWordsImagesAndOnlyDeclaredFormatting() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Path epub = buildStatsEpub(tempDir.resolve("stats.epub"));

        final Document document = pair.reader().read(epub);
        final BookStats stats = pair.inspection().stats(document);

        assertThat(stats.segments()).isEqualTo(TOTAL_SEGMENTS);
        assertThat(stats.words()).isEqualTo(TOTAL_WORDS);
        assertThat(stats.images()).isEqualTo(IMAGE_COUNT);
        assertThat(stats.fonts()).isZero();
        assertThat(stats.codeBlocks()).isZero();
        assertThat(stats.verseLines()).isZero();
        assertThat(stats.footnotes()).isZero();
        assertThat(stats.tables()).isZero();
        assertThat(stats.formatting()).containsExactlyInAnyOrder(Formatting.ITALICS, Formatting.QUOTES);
    }

    private static Path buildStatsEpub(Path destination) {
        final EpubZipBuilder builder = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf());
        int wordIndex = 0;
        int paragraphIndex = 0;
        for (int chapter = 1; chapter <= CHAPTER_COUNT; chapter++) {
            final StringBuilder body = new StringBuilder();
            for (int p = 0; p < PARAGRAPHS_PER_CHAPTER; p++) {
                final int words = paragraphIndex < EXTRA_WORD_PARAGRAPHS
                        ? BASE_WORDS_PER_PARAGRAPH + 1
                        : BASE_WORDS_PER_PARAGRAPH;
                body.append(paragraphOf(wordIndex, words, paragraphIndex));
                wordIndex += words;
                paragraphIndex++;
            }
            builder.entry("OEBPS/c%02d.xhtml".formatted(chapter), xhtml(body.toString()));
        }
        return builder.writeTo(destination);
    }

    /** The very first paragraph wraps one word in {@code <i>} and one in {@code <q>}; every other word is plain. */
    private static String paragraphOf(int startIndex, int wordCount, int paragraphIndex) {
        final List<String> tokens = new ArrayList<>();
        for (int i = 0; i < wordCount; i++) {
            tokens.add("alpha" + (startIndex + i));
        }
        if (paragraphIndex == 0 && tokens.size() > 1) {
            tokens.set(0, "<i>" + tokens.get(0) + "</i>");
            tokens.set(1, "<q>" + tokens.get(1) + "</q>");
        }
        return "<p>" + String.join(" ", tokens) + ".</p>";
    }

    private static String xhtml(String bodyInner) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body>%s</body>
                </html>
                """.formatted(bodyInner);
    }

    private static String opf() {
        final StringBuilder manifest = new StringBuilder();
        for (int chapter = 1; chapter <= CHAPTER_COUNT; chapter++) {
            manifest.append("<item id=\"c%02d\" href=\"c%02d.xhtml\" media-type=\"application/xhtml+xml\"/>"
                    .formatted(chapter, chapter));
        }
        for (int image = 1; image <= IMAGE_COUNT; image++) {
            manifest.append(
                    "<item id=\"img%d\" href=\"images/img%d.jpg\" media-type=\"image/jpeg\"/>".formatted(image, image));
        }
        final StringBuilder spine = new StringBuilder();
        for (int chapter = 1; chapter <= CHAPTER_COUNT; chapter++) {
            spine.append("<itemref idref=\"c%02d\"/>".formatted(chapter));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Stats Fixture</dc:title></metadata>
                  <manifest>%s</manifest>
                  <spine>%s</spine>
                </package>
                """.formatted(manifest, spine);
    }

    @Test
    void stats_maskedFormWithInterleavedPlaceholders_countsTwoWords() {
        final Segment segment = segmentOf("⟦g0⟧old⟦g1⟧ door");

        assertThat(WordCounter.count(List.of(segment), null)).isEqualTo(2);
    }

    private static Segment segmentOf(String masked) {
        return new Segment(
                "unit:0",
                "unit",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static final String TECHNICAL_MARKDOWN = """
            # Title

            ```
            code one
            ```

            Some text.

            ```
            code two
            ```

            More text.

            ```
            code three
            ```

            | A | B |
            |---|---|
            | 1 | 2 |
            """;

    @Test
    void stats_markdownWithFencedCodeAndTable_countsCodeBlocksAndTable() {
        final Path source = write(tempDir.resolve("technical.md"), TECHNICAL_MARKDOWN);
        final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();
        final MarkdownReader reader = new MarkdownReader(registry);
        final MarkdownInspection inspection = new MarkdownInspection(registry);

        final Document document = reader.read(source);
        final BookStats stats = inspection.stats(document);

        assertThat(stats.codeBlocks()).isEqualTo(3);
        assertThat(stats.tables()).isEqualTo(1);
    }

    private static final String FB2_STATS_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:l="http://www.w3.org/1999/xlink">
              <description><title-info><book-title>Sample</book-title></title-info></description>
              <body>
                <section>
                  <title><p>Chapter 1</p></title>
                  <p>One <emphasis>two</emphasis> <strong>three</strong> <sub>four</sub>.</p>
                  <poem><stanza><v>Line one</v><v>Line two</v></stanza></poem>
                  <p>See<a l:href="#n1">note</a>.</p>
                </section>
              </body>
              <body name="notes">
                <section id="n1"><title><p>Note</p></title><p>Note text.</p></section>
              </body>
              <binary id="cover.jpg" content-type="image/jpeg">Zm9v</binary>
              <binary id="font.otf" content-type="application/x-font-otf">Zm9v</binary>
            </FictionBook>
            """;

    @Test
    void stats_fb2Fixture_countsImagesVerseLinesFootnotesAndFormatting() {
        final Path fb2 = Fb2Fixtures.writeFb2(tempDir.resolve("stats.fb2"), FB2_STATS_XML, StandardCharsets.UTF_8);
        final OpenFb2Registry registry = new OpenFb2Registry();
        final Fb2Reader reader = new Fb2Reader(registry);
        final Fb2Inspection inspection = new Fb2Inspection(registry);

        final Document document = reader.read(fb2);
        final BookStats stats = inspection.stats(document);

        assertThat(stats.images()).isEqualTo(1);
        assertThat(stats.fonts()).isZero();
        assertThat(stats.codeBlocks()).isZero();
        assertThat(stats.tables()).isZero();
        assertThat(stats.verseLines()).isEqualTo(2);
        assertThat(stats.footnotes()).isEqualTo(1);
        assertThat(stats.formatting())
                .containsExactlyInAnyOrder(Formatting.ITALICS, Formatting.BOLD, Formatting.LINKS, Formatting.OTHER);

        final java.util.Set<String> ids = inspection.resourceIds(document);
        assertThat(ids).contains("cover.jpg", "font.otf", "n1");
    }

    @Test
    void stats_markdownWithImageEmphasisAndLink_countsFormattingAndResourceIds() {
        final String markdown = "See ![cover](images/cover.png) and *emphasis* and [a link](https://example.org).\n";
        final Path source = write(tempDir.resolve("formatted.md"), markdown);
        final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();
        final MarkdownReader reader = new MarkdownReader(registry);
        final MarkdownInspection inspection = new MarkdownInspection(registry);

        final Document document = reader.read(source);
        final BookStats stats = inspection.stats(document);
        final java.util.Set<String> resourceIds = inspection.resourceIds(document);

        assertThat(stats.images()).isEqualTo(1);
        assertThat(stats.formatting()).contains(Formatting.ITALICS, Formatting.LINKS);
        assertThat(resourceIds).containsExactly("images/cover.png");
    }

    private static Path write(Path destination, String content) {
        try {
            Files.writeString(destination, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}
