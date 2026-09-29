package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.LockedRendering;
import ua.bookloom.pipeline.qa.QaResult;

/** The glossary check reads the masked form with each document token as a space, so a footnote marker splits no word. */
class QaEvaluationTest {

    private static final String SOURCE = "Hale⟦g0⟧1⟦g1⟧ opened the door.";
    private static final List<LockedRendering> HALE = List.of(new LockedRendering("Hale", "Гейл"));

    private static Segment segment() {
        return new Segment(
                "ch01.xhtml:0",
                "ch01.xhtml",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "h0",
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static CheckResult glossaryResult(final String maskedCandidate, final String maskedForm) {
        final QaResult result = QaEvaluation.evaluate(
                List.of(),
                segment(),
                SOURCE,
                maskedCandidate,
                maskedForm,
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.BALANCED),
                HALE);
        return result.soft().stream()
                .filter(check -> check.check() == CheckName.GLOSSARY)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void evaluate_lockedRenderingNextToAFootnoteMarker_glossaryCheckPassesAtFullMargin() {
        final String form = "Гейл⟦g0⟧1⟦g1⟧ відчинив двері.";

        final CheckResult glossary = glossaryResult(form, form);

        assertThat(glossary.passed()).isTrue();
        assertThat(glossary.skipped()).isFalse();
        assertThat(glossary.margin()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void evaluate_renderingMissingBesideAFootnoteMarker_glossaryCheckFails() {
        final String form = "Хейл⟦g0⟧1⟦g1⟧ відчинив двері.";

        final CheckResult glossary = glossaryResult(form, form);

        assertThat(glossary.passed()).isFalse();
    }
}
