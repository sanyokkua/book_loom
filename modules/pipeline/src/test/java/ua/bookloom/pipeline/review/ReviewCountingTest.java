package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/** The one counts rule the review desk and the export report share. */
class ReviewCountingTest {

    @Test
    void count_elevenBodyRecordsAndTwoKeptFrontmatterValues_countsEachRecordOnce() {
        // 6 first-draft accepts, 1 repaired, 3 flagged of which the person accepted 1 and 1 has no target, 1 pending, 2
        // kept
        final List<SegmentRecord> records = new ArrayList<>();
        IntStream.range(0, 6)
                .forEach(index -> records.add(body("a" + index, SegmentStatus.ACCEPTED, SegmentPath.DRAFT)));
        records.add(body("r", SegmentStatus.ACCEPTED, SegmentPath.REPAIRED));
        records.add(body("f1", SegmentStatus.FLAGGED, SegmentPath.REPAIRED));
        records.add(body("f2", SegmentStatus.ACCEPTED, SegmentPath.REPAIRED).withReviewed(true));
        records.add(body("f3", SegmentStatus.FLAGGED, SegmentPath.DRAFT).withMachineTarget(null, null));
        records.add(body("p", SegmentStatus.PENDING, SegmentPath.DRAFT).withMachineTarget(null, null));
        records.add(frontmatter("k1"));
        records.add(frontmatter("k2"));

        final ReviewCounts counts =
                ReviewCounting.count(records, AlsoTranslate.defaults().keptKinds());

        assertThat(counts).isEqualTo(new ReviewCounts(13, 6, 1, 2, 1, 1, 2, 1));
    }

    @Test
    void count_noRecords_isAllZero() {
        // an empty project has nothing in any tile
        assertThat(ReviewCounting.count(List.of(), AlsoTranslate.defaults().keptKinds()))
                .isEqualTo(new ReviewCounts(0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void count_tmReuseAndRevisedRecords_areAutoAcceptedAndReviewedRespectively() {
        // TM reuse is an auto-accept; a REVISED record is reviewed and in no other tile
        final List<SegmentRecord> records = List.of(
                body("t", SegmentStatus.ACCEPTED, SegmentPath.TM_REUSE),
                body("v", SegmentStatus.REVISED, SegmentPath.USER).withReviewed(true));

        assertThat(ReviewCounting.count(records, AlsoTranslate.defaults().keptKinds()))
                .isEqualTo(new ReviewCounts(2, 1, 0, 0, 1, 0, 0, 0));
    }

    @Test
    void count_keptRecordThatWasDecidedBefore_isCountedOnlyAsSourceKept() {
        // a record kept as source by choice is counted in no other field, whatever its status
        final SegmentRecord kept =
                frontmatter("k").withStatus(SegmentStatus.ACCEPTED).withReviewed(true);

        assertThat(ReviewCounting.count(List.of(kept), AlsoTranslate.defaults().keptKinds()))
                .isEqualTo(new ReviewCounts(1, 0, 0, 0, 0, 0, 1, 0));
    }

    private static SegmentRecord body(final String id, final SegmentStatus status, final SegmentPath path) {
        return record(id, "unit-1", SegmentKind.PARAGRAPH, status, path);
    }

    private static SegmentRecord frontmatter(final String id) {
        return record(id, Unit.AUXILIARY_ID, SegmentKind.FRONTMATTER_VALUE, SegmentStatus.PENDING, SegmentPath.DRAFT);
    }

    private static SegmentRecord record(
            final String id,
            final String unitId,
            final SegmentKind kind,
            final SegmentStatus status,
            final SegmentPath path) {
        return new SegmentRecord(
                "p1", id, unitId, 0, kind, status, "Ціль.", "Ціль.", null, null, 0.9, null, List.of(), path, 0, false,
                null);
    }
}
