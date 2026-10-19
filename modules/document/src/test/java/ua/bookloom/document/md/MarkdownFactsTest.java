package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.md.MarkdownAuxHarness.auxOf;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;

/** The year and the publisher a Markdown frontmatter declares reach the metadata map, and no segment is added. */
class MarkdownFactsTest {

    @TempDir
    private Path tempDir;

    private MarkdownAuxHarness harness;

    @BeforeEach
    void setUp() {
        harness = new MarkdownAuxHarness(tempDir);
    }

    private Document open(final String... frontmatterLines) {
        return harness.open("---\n" + String.join("\n", frontmatterLines) + "\n---\nThe lamp burned low.\n");
    }

    private static List<String> segmentShape(final Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .map(segment -> segment.id() + "|" + segment.kind() + "|" + segment.sourceInner())
                .toList();
    }

    // IF the frontmatter has a full date and a publisher, THEN the map carries the bare year and the publisher.
    @Test
    void read_dateAndPublisher_carriesTheYearAndThePublisher() {
        final Document document = open("title: The Lamp", "date: 2021-06-05", "publisher: Larkspur Press");

        assertThat(document.metadata())
                .containsEntry(MetadataKey.DATE.key(), "2021")
                .containsEntry(MetadataKey.PUBLISHER.key(), "Larkspur Press")
                .containsEntry(MetadataKey.TITLE.key(), "The Lamp");
    }

    // IF the values are quoted or the date is worded, THEN the quotes go and the first four-digit run is the year.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"\"June 1898\"|1898", "'1850-1852'|1850", "1999|1999", "2019-03-05T10:00:00Z|2019"})
    void read_dateInSeveralWritings_keepsTheFirstFourDigitYear(final String written, final String year) {
        final Document document = open("date: " + written);

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), year);
    }

    // IF the date has no four-digit year, THEN the book has no year from it.
    @ParameterizedTest
    @ValueSource(strings = {"someday", "19950", "20190305"})
    void read_dateWithNoFourDigitYear_hasNoDateKey(final String written) {
        final Document document = open("date: " + written);

        assertThat(document.metadata()).doesNotContainKey(MetadataKey.DATE.key());
    }

    // IF the publisher is quoted, THEN the quotes are not part of it.
    @Test
    void read_quotedPublisher_dropsTheQuotes() {
        final Document document = open("publisher: \"Larkspur Press\"");

        assertThat(document.metadata()).containsEntry(MetadataKey.PUBLISHER.key(), "Larkspur Press");
    }

    // IF the frontmatter has neither, THEN the map has neither key.
    @Test
    void read_noDateAndNoPublisher_hasNeitherKey() {
        final Document document = open("title: The Lamp");

        assertThat(document.metadata()).doesNotContainKeys(MetadataKey.DATE.key(), MetadataKey.PUBLISHER.key());
    }

    // IF a book has no frontmatter at all, THEN the map has neither key.
    @Test
    void read_noFrontmatter_hasNeitherKey() {
        final Document document = harness.open("The lamp burned low.\n");

        assertThat(document.metadata()).doesNotContainKeys(MetadataKey.DATE.key(), MetadataKey.PUBLISHER.key());
    }

    // IF a date is added to the frontmatter, THEN no segment appears: a typed scalar was never text to translate.
    @Test
    void read_dateAdded_addsNoSegment() {
        final Document plain = open("title: The Lamp");
        final Document withDate = open("title: The Lamp", "date: 2021-06-05");

        assertThat(segmentShape(withDate)).isNotEmpty().containsExactlyElementsOf(segmentShape(plain));
    }

    // IF a plain-text publisher is in the frontmatter, THEN it is still the FRONTMATTER_VALUE segment it was before
    // (translated only when "Also translate frontmatter" is on), and the metadata map reads it as well.
    @Test
    void read_plainTextPublisher_staysOneFrontmatterValueSegmentAndIsInTheMap() {
        final Document document = open("title: The Lamp", "publisher: Larkspur Press");

        assertThat(auxOf(document))
                .extracting(Segment::id, Segment::kind, Segment::sourceInner)
                .contains(org.assertj.core.api.Assertions.tuple(
                        "aux:fm:publisher", SegmentKind.FRONTMATTER_VALUE, "Larkspur Press"));
        assertThat(auxOf(document)).hasSize(2);
        assertThat(document.metadata()).containsEntry(MetadataKey.PUBLISHER.key(), "Larkspur Press");
    }
}
