package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;

/**
 * {@link SegmentView}'s defensive copy of its findings list.
 */
class SegmentViewTest {

    @Test
    void constructor_findingsList_isDefensivelyCopied() {
        final QaFinding finding = new QaFinding("placeholder", Severity.HIGH, "missing token", "placeholder");
        final List<QaFinding> findings = new ArrayList<>(List.of(finding));

        final SegmentView view = new SegmentView(
                "ch1.xhtml:0",
                "ch1 · p01",
                SegmentKind.PARAGRAPH,
                SegmentStatus.FLAGGED,
                "masked source",
                "display source",
                null,
                null,
                null,
                findings,
                null,
                SegmentPath.DRAFT,
                false,
                null,
                null);
        findings.clear();

        assertThat(view.findings()).containsExactly(finding);
    }
}
