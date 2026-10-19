package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.md.MarkdownAuxHarness.auxOf;
import static ua.bookloom.document.md.MarkdownAuxHarness.auxSources;
import static ua.bookloom.document.md.MarkdownAuxHarness.bodyOf;
import static ua.bookloom.document.md.MarkdownAuxHarness.restored;
import static ua.bookloom.document.md.MarkdownAuxHarness.withTargets;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;

/** Markdown image descriptions as auxiliary segments, and how a translated one goes back between its brackets. */
class MarkdownAltAuxTest {

    private static final String ALT_ID = "aux:alt:book.md:img0";
    private static final String BODY_ID = "book.md:0";

    @TempDir
    private Path tempDir;

    private MarkdownAuxHarness harness;

    @BeforeEach
    void setUp() {
        harness = new MarkdownAuxHarness(tempDir);
    }

    @Test
    void read_image_yieldsAnAltSegmentAddressedByByteSpan() {
        final Document document = harness.open("Привіт ![Figure 1](fig1.png)\n");

        final Segment alt = auxOf(document).get(0);
        assertThat(alt.id()).isEqualTo(ALT_ID);
        assertThat(alt.kind()).isEqualTo(SegmentKind.ALT);
        assertThat(alt.sourceInner()).isEqualTo("Figure 1");
        assertThat(alt.anchor()).isEqualTo(new ByteSpanAnchor(15, 23));
    }

    @Test
    void read_escapedBracketsInAlt_areReadAsBrackets() {
        final Document document = harness.open("![Figure \\[1\\]](fig1.png)\n");

        assertThat(auxOf(document)).extracting(Segment::masked).containsExactly("Figure [1]");
    }

    @Test
    void read_imagesWithAndWithoutAlt_countEveryImageButProduceOnlyNonEmptyAlts() {
        final Document document = harness.open("![](a.png)\n\n![Second](b.png)\n");

        assertThat(auxOf(document)).extracting(Segment::id).containsExactly("aux:alt:book.md:img1");
        assertThat(auxSources(document)).containsExactly("Second");
    }

    @Test
    void read_imageInsideCodeFence_yieldsNoSegment() {
        final Document document = harness.open("```\n![Figure 1](fig1.png)\n```\n");

        assertThat(auxOf(document)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Рисунок 1|![Рисунок 1](fig1.png)",
                "Рисунок [1]|![Рисунок \\[1\\]](fig1.png)",
                "Шлях \\ назад|![Шлях \\\\ назад](fig1.png)"
            })
    void write_altTargetWithNoBodyTarget_replacesOnlyTheAltSpan(String target, String expectedImage) {
        final Document document = harness.open("Before ![Figure 1](fig1.png) after\n");

        final String output = harness.export(withTargets(document, Map.of(ALT_ID, target)), "uk");

        assertThat(output).isEqualTo("Before " + expectedImage + " after\n");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"До ⟦g0⟧ після|До ![Рисунок 1](fig1.png) після", "⟦g0⟧ До після|![Рисунок 1](fig1.png) До після"})
    void write_altTargetInsideATranslatedParagraph_isWrittenInsideTheRestoredImage(String masked, String expected) {
        final Document document = harness.open("Before ![Figure 1](fig1.png) after\n");
        final String body = restored(bodyOf(document).get(0), masked);

        final String output = harness.export(withTargets(document, Map.of(BODY_ID, body, ALT_ID, "Рисунок 1")), "uk");

        assertThat(output).isEqualTo(expected + "\n");
    }

    @Test
    void write_bracketInAltInsideATranslatedParagraph_isEscapedOnce() {
        final Document document = harness.open("Before ![Figure 1](fig1.png) after\n");
        final String body = restored(bodyOf(document).get(0), "До ⟦g0⟧ після");

        final String output = harness.export(withTargets(document, Map.of(BODY_ID, body, ALT_ID, "Рисунок [1]")), "uk");

        assertThat(output).isEqualTo("До ![Рисунок \\[1\\]](fig1.png) після\n");
    }

    @Test
    void write_identicalImagesInATranslatedParagraph_takeTheirDescriptionsInOrder() {
        final Document document = harness.open("![Dot](dot.png) and ![Dot](dot.png)\n");
        final String body = restored(bodyOf(document).get(0), "⟦g0⟧ і ⟦g1⟧");

        final String output = harness.export(
                withTargets(
                        document, Map.of(BODY_ID, body, ALT_ID, "Перша точка", "aux:alt:book.md:img1", "Друга точка")),
                "uk");

        assertThat(output).isEqualTo("![Перша точка](dot.png) і ![Друга точка](dot.png)\n");
    }

    @Test
    void write_altTargetForOnlyTheSecondOfTwoImagesInATranslatedParagraph_leavesTheFirstAsSource() {
        final Document document = harness.open("![Dot](dot.png) and ![Dot](dot.png)\n");
        final String body = restored(bodyOf(document).get(0), "⟦g0⟧ і ⟦g1⟧");

        final String output =
                harness.export(withTargets(document, Map.of(BODY_ID, body, "aux:alt:book.md:img1", "Друга")), "uk");

        assertThat(output).isEqualTo("![Dot](dot.png) і ![Друга](dot.png)\n");
    }

    @Test
    void write_altTargetAndBodyTargetInDifferentParagraphs_bothAreWritten() {
        final Document document = harness.open("![Figure 1](fig1.png)\n\nSecond paragraph.\n");

        final String output =
                harness.export(withTargets(document, Map.of(ALT_ID, "Рисунок 1", "book.md:1", "Другий абзац.")), "uk");

        assertThat(output).isEqualTo("![Рисунок 1](fig1.png)\n\nДругий абзац.\n");
    }

    @Test
    void write_zeroEditWithImage_isByteIdentical() {
        final String source = "Before ![Figure 1](fig1.png) after\n";

        assertThat(harness.export(harness.open(source), "uk")).isEqualTo(source);
    }

    @Test
    void unmask_altText_keepsBracketsAndBackslashesAsPlainText() {
        final Segment alt = auxOf(harness.open("![Figure 1](fig1.png)\n")).get(0);

        assertThat(restored(alt, "Рисунок [1] *х*")).isEqualTo("Рисунок [1] *х*");
    }
}
