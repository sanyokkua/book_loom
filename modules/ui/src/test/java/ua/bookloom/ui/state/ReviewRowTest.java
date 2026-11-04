package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.ui.ReviewFixtures;

/** What a desk row carries from its segment: the badge of a flagged one, the doubting check of an accepted one. */
class ReviewRowTest {

    private static SegmentView with(final SegmentStatus status, final String raisedBy) {
        return ReviewFixtures.view(
                "s1",
                "ch1 · p1",
                status,
                List.of(new QaFinding("omission", Severity.MEDIUM, "note", raisedBy)),
                null,
                null);
    }

    @Test
    void of_acceptedWithAuditFinding_carriesDoubtCheck() {
        final ReviewRow row = ReviewRow.of(with(SegmentStatus.ACCEPTED, "audit:negation"), false);

        assertThat(row.doubtCheck()).isEqualTo("negation");
        assertThat(row.badge()).isNull();
    }

    @Test
    void of_flaggedWithAuditFinding_carriesBadgeAndNoDoubtCheck() {
        final ReviewRow row = ReviewRow.of(with(SegmentStatus.FLAGGED, "audit:negation"), false);

        assertThat(row.doubtCheck()).isNull();
        assertThat(row.badge()).isEqualTo(FindingBadge.OMISSION);
    }

    @Test
    void of_acceptedWithOnlyRunFindings_carriesNoDoubtCheck() {
        assertThat(ReviewRow.of(with(SegmentStatus.ACCEPTED, "glossary"), false).doubtCheck())
                .isNull();
    }
}
