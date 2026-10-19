package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.md.MarkdownAuxHarness.auxOf;
import static ua.bookloom.document.md.MarkdownAuxHarness.auxSources;
import static ua.bookloom.document.md.MarkdownAuxHarness.bodyOf;
import static ua.bookloom.document.md.MarkdownAuxHarness.restored;
import static ua.bookloom.document.md.MarkdownAuxHarness.withTargets;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.document.fixture.MarkdownFixtures;

/** Which Markdown frontmatter values become auxiliary segments, and how a translation goes back into YAML. */
class MarkdownFrontmatterAuxTest {

    @TempDir
    private Path tempDir;

    private MarkdownAuxHarness harness;

    @BeforeEach
    void setUp() {
        harness = new MarkdownAuxHarness(tempDir);
    }

    private static String front(String... lines) {
        return "---\n" + String.join("\n", lines) + "\n---\nProse.\n";
    }

    @Test
    void read_primaryFixture_yieldsOnlyTheTitleValueAsSegment() {
        final Document document = harness.open(MarkdownFixtures.PRIMARY);

        assertThat(auxOf(document)).extracting(Segment::id).containsExactly("aux:fm:title");
        assertThat(auxSources(document)).containsExactly("The Book");
        assertThat(auxOf(document).get(0).kind()).isEqualTo(SegmentKind.FRONTMATTER_VALUE);
        assertThat(bodyOf(document)).extracting(Segment::sourceInner).doesNotContain("The Book", "title", "lang", "en");
    }

    @Test
    void read_dateAndBooleanNextToTitleAndLang_yieldsExactlyTheTitle() {
        final Document document = harness.open("---\ntitle: The Book\nlang: en\ndate: 2024-05-01\ndraft: true\n---\n");

        assertThat(auxSources(document)).containsExactly("The Book");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "tags: [war, peace]",
                "homepage: https://example.com",
                "pages: 1225",
                "published: yes",
                "summary: |\n  A long story.",
                "folded: >\n  A long story.",
                "owner: me@example.org",
                "site: www.example.com",
                "mail: mailto:me@example.org",
                "nothing: ~",
                "nothing: NULL",
                "nothing:",
                "hex: 0xFF",
                "octal: 0o17",
                "big: .inf",
                "not: .nan",
                "when: 2024-05-01T10:00:00Z",
                "flag: Off",
                "letter: n",
                "anchor: &a Foo",
                "alias: *a",
                "tagged: !!str Foo",
                "inline: {a: b}",
                "language: en",
                "lang: en",
                "quoted: \"1984\"",
                "quotedUrl: \"https://example.com\"",
                "list:\n  - Alpha\n  - Beta",
                "parent:\n  child: Nested value"
            })
    void read_valueThatIsNotText_yieldsNoSegment(String lines) {
        final Document document = harness.open(front(lines));

        assertThat(auxOf(document)).isEmpty();
    }

    @Test
    void read_quotedValueWithALetterAndOneWithout_yieldsOnlyTheLetterOne() {
        final Document document = harness.open(front("subtitle: \"Part 2\"", "edition: \"2\""));

        assertThat(auxSources(document)).containsExactly("Part 2");
    }

    @Test
    void read_trailingComment_isNotPartOfTheValue() {
        final Document document = harness.open(front("title: The Book # working title"));

        assertThat(auxSources(document)).containsExactly("The Book");
    }

    @Test
    void read_thematicBreakLaterInTheFile_yieldsNoAuxiliaryAndTwoBodySegments() {
        final Document document = harness.open("# Title\n\nFirst.\n\n---\ntitle: The Book\n---\n\nSecond.\n");

        assertThat(auxOf(document)).isEmpty();
        assertThat(bodyOf(document)).extracting(Segment::sourceInner).contains("First.", "Second.");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "title: \"The \\\"Old\\\" House\"|The \"Old\" House",
                "title: 'It''s here'|It's here",
                "title: Plain words|Plain words"
            })
    void read_quotedValue_isReadInsideItsQuotesWithEscapesDecoded(String line, String expected) {
        final Document document = harness.open(front(line));

        assertThat(auxOf(document)).extracting(Segment::masked).containsExactly(expected);
        assertThat(auxSources(document)).containsExactly(expected);
    }

    @Test
    void read_frontmatterValue_isNamedByItsKey() {
        final Document document = harness.open(front("title: The Book", "author: Some One"));

        assertThat(auxOf(document)).extracting(Segment::id).containsExactly("aux:fm:title", "aux:fm:author");
    }

    @Test
    void write_zeroEditWithTargetLanguage_changesOnlyTheLangValue() {
        final Document document = harness.open(MarkdownFixtures.PRIMARY);

        assertThat(harness.export(document, "uk")).isEqualTo(MarkdownFixtures.PRIMARY.replace("lang: en", "lang: uk"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "title: The Book|Книга|title: Книга",
                "title: The Book|Книга: Частина 1|title: \"Книга: Частина 1\"",
                "title: Nineteen Eighty-Four|1984|title: \"1984\"",
                "title: The Book|yes|title: \"yes\"",
                "title: The Book|2024-05-01|title: \"2024-05-01\"",
                "title: The Book|Книга #1 ok|title: \"Книга #1 ok\"",
                "title: The Book|- Книга|title: \"- Книга\"",
                "title: The Book|@Книга|title: \"@Книга\"",
                "title: \"The Book\"|Книга \"Перша\"|title: \"Книга \\\"Перша\\\"\"",
                "title: 'The Book'|Книга 'Перша'|title: 'Книга ''Перша'''",
                "title: The Book|Том & Джеррі *назавжди*|title: Том & Джеррі *назавжди*",
                "title: The Book # working title|Книга|title: Книга # working title"
            })
    void write_translatedValue_isQuotedSoItStillReadsAsText(String line, String target, String expectedLine) {
        final Document document = harness.open(front(line));

        final String output = harness.export(withTargets(document, Map.of("aux:fm:title", target)), "uk");

        assertThat(output).isEqualTo(front(expectedLine));
    }

    @Test
    void write_titleOnlyFrontmatterWithNoEdit_isUnchangedForUk() {
        final Document document = harness.open("---\ntitle: The Book\n---\n");

        assertThat(harness.export(document, "uk")).isEqualTo("---\ntitle: The Book\n---\n");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "lang: en|lang: uk",
                "lang: \"en-US\"|lang: \"uk\"",
                "lang: 'en'|lang: 'uk'",
                "lang: en # source|lang: uk # source",
                "language: en|language: en"
            })
    void write_langKey_isReplacedInItsQuoteStyleAndLanguageIsLeftAlone(String line, String expectedLine) {
        final Document document = harness.open(front("title: The Book", line));

        assertThat(harness.export(document, "uk")).isEqualTo(front("title: The Book", expectedLine));
    }

    @Test
    void write_frontmatterWithoutLang_gainsNoLangKey() {
        final Document document = harness.open(front("title: The Book", "language: en"));

        assertThat(harness.export(document, "uk")).doesNotContain("lang:").contains("language: en");
    }

    @Test
    void write_windowsLineEndsInFrontmatter_keepsThemAroundTheReplacedValue() {
        final String source = "---\r\ntitle: The Book\r\nlang: en\r\n---\r\nProse.\r\n";
        final Document document = harness.open(source);

        final String output = harness.export(withTargets(document, Map.of("aux:fm:title", "Книга")), "uk");

        assertThat(output).isEqualTo("---\r\ntitle: Книга\r\nlang: uk\r\n---\r\nProse.\r\n");
    }

    @Test
    void write_valueAfterMultibyteText_landsOnTheRightBytes() {
        final Document document = harness.open("---\nauthor: Лев Толстой\ntitle: The Book\n---\nПроза.\n");

        final String output = harness.export(withTargets(document, Map.of("aux:fm:title", "Книга")), "uk");

        assertThat(output).isEqualTo("---\nauthor: Лев Толстой\ntitle: Книга\n---\nПроза.\n");
    }

    @Test
    void unmask_frontmatterValue_gainsNoMarkdownBackslashes() {
        final Segment segment = auxOf(harness.open(front("title: The Book"))).get(0);

        assertThat(restored(segment, "Том & Джеррі *назавжди*")).isEqualTo("Том & Джеррі *назавжди*");
    }

    @Test
    void read_auxiliarySegments_keepTheirUnitAndChainTheirNeighbours() {
        final List<Segment> aux = auxOf(harness.open(front("title: The Book", "author: Some One")));

        assertThat(aux.get(0).nextKey()).isEqualTo("aux:fm:author");
        assertThat(aux.get(1).prevKey()).isEqualTo("aux:fm:title");
        assertThat(aux).extracting(Segment::unit).containsOnly("aux");
    }
}
