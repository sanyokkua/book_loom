package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.DOOR_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.FOREIGN_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.NAMES_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.SCRIPT_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.finding;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.update;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The queue and segment reads over a real opened book, in document order and by the brief's current switches. */
class ReviewQueriesTest {

    @TempDir
    private Path tempDir;

    @Test
    void queue_allFlagged_listsFlaggedSegmentsInDocumentOrderWithTheirLocators() {
        // three FLAGGED segments read as chapter and paragraph, never as ids
        final Desk desk = flaggedThree();

        final List<SegmentView> views = queue(desk, ReviewFilter.ALL_FLAGGED);

        assertThat(views).extracting(SegmentView::segmentId).containsExactly(FLAGGED_ID, NAMES_ID, SCRIPT_ID);
        assertThat(views).extracting(SegmentView::locator).containsExactly("ch5 · p12", "ch7 · p40", "ch9 · p03");
        assertThat(views.getFirst().judgeScore()).isEqualTo(0.58);
    }

    @Test
    void queue_names_listsOnlyTheSegmentWithAGlossaryFinding() {
        // NAMES narrows to segments with a glossary finding
        final Desk desk = flaggedThree();

        assertThat(queue(desk, ReviewFilter.NAMES))
                .extracting(SegmentView::segmentId)
                .containsExactly(NAMES_ID);
    }

    @Test
    void queue_omissions_listsOnlyTheSegmentWithAnOmissionFinding() {
        // OMISSIONS narrows to segments with an omission finding
        final Desk desk = flaggedThree();
        update(desk, FLAGGED_ID, record -> record.withFindings(List.of(finding("omission", "length"))));

        assertThat(queue(desk, ReviewFilter.OMISSIONS))
                .extracting(SegmentView::segmentId)
                .containsExactly(FLAGGED_ID);
    }

    @Test
    void queue_foreignKept_listsOnlyTheSegmentHoldingAKeptForeignRun() {
        // au revoir is a kept French run, and it is the only foreign text among the flagged segments
        final Desk desk = flaggedThree();
        flag(desk, FOREIGN_ID, "Вона прошепотіла au revoir і пішла.");

        assertThat(queue(desk, ReviewFilter.FOREIGN_KEPT))
                .extracting(SegmentView::locator)
                .containsExactly("ch11 · p02");
    }

    @Test
    void queue_foreignKeptWhenTheParagraphIsWrittenInAnotherScript_listsTheMarkedSegment() {
        // a Cyrillic paragraph in a Latin-script English book is marked foreign, though it holds no kept run
        final Desk desk = ReviewFixtures.epubOf(tempDir, List.of("Привіт, друже.", "Thanks."));
        flag(desk, "OEBPS/ch0.xhtml:0", "Привіт.");
        flag(desk, "OEBPS/ch0.xhtml:1", "Дякую.");

        assertThat(queue(desk, ReviewFilter.FOREIGN_KEPT))
                .extracting(SegmentView::segmentId)
                .containsExactly("OEBPS/ch0.xhtml:0");
    }

    @Test
    void queue_allSegments_listsDecidedRecordsButNotPendingOnes() {
        // ALL_SEGMENTS is every decided record; a segment the run has not reached is not listed
        final Desk desk = flaggedThree();
        update(desk, "ch02.xhtml:3", record -> record.withStatus(SegmentStatus.ACCEPTED));

        assertThat(queue(desk, ReviewFilter.ALL_SEGMENTS))
                .extracting(SegmentView::segmentId)
                .containsExactly("ch02.xhtml:3", FLAGGED_ID, NAMES_ID, SCRIPT_ID);
    }

    @Test
    void queue_allSegmentsWithNavigationLabelsSwitchedOff_listsSevenLabelsAsSourceKept() {
        // records kept as source by choice appear only under ALL_SEGMENTS, with path SOURCE_KEPT
        final Desk desk = ReviewFixtures.epubWithNavigation(
                tempDir, ReviewFixtures.brief(new AlsoTranslate(false, true, true, false)));

        final List<SegmentView> labels = queue(desk, ReviewFilter.ALL_SEGMENTS).stream()
                .filter(view -> view.locator().startsWith("nav"))
                .toList();

        assertThat(labels)
                .extracting(SegmentView::locator)
                .containsExactly("nav · 01", "nav · 02", "nav · 03", "nav · 04", "nav · 05", "nav · 06", "nav · 07");
        assertThat(labels).extracting(SegmentView::path).containsOnly(SegmentPath.SOURCE_KEPT);
    }

    @ParameterizedTest
    @EnumSource(
            value = ReviewFilter.class,
            names = {"ALL_FLAGGED", "NAMES", "OMISSIONS", "FOREIGN_KEPT"})
    void queue_filterOtherThanAllSegments_neverListsARecordKeptAsSource(final ReviewFilter filter) {
        // a kept record is neither a problem nor unfinished work, so no chip lists it
        final Desk desk = ReviewFixtures.epubWithNavigation(
                tempDir, ReviewFixtures.brief(new AlsoTranslate(false, true, true, false)));

        assertThat(queue(desk, filter)).extracting(SegmentView::path).doesNotContain(SegmentPath.SOURCE_KEPT);
    }

    @Test
    void segment_doorSentence_carriesMaskedAndDisplaySource() {
        // the masked source keeps the book's tokens, the display source is the text a person reads
        final Desk desk = ReviewFixtures.epub(tempDir);

        final SegmentView view = Objects.requireNonNull(
                desk.queries().segment(desk.projectId(), DOOR_ID).data());

        assertThat(view.maskedSource()).isEqualTo("He opened the ⟦g0⟧old⟦g1⟧ door.");
        assertThat(view.displaySource()).isEqualTo("He opened the old door.");
        assertThat(view.locator()).isEqualTo("ch7 · p41");
        assertThat(view.status()).isEqualTo(SegmentStatus.PENDING);
    }

    @Test
    void segment_withAnOpenProposal_carriesTheProposal() {
        // the proposal the review panel shows beside the person's text
        final Desk desk = ReviewFixtures.epub(tempDir);
        desk.deferrals()
                .add(new Deferral(
                        "d1",
                        desk.projectId(),
                        DOOR_ID,
                        DeferralReason.GENDER_UNKNOWN,
                        null,
                        null,
                        "Він пішов.",
                        "Він пішов."));

        assertThat(Objects.requireNonNull(desk.queries()
                                .segment(desk.projectId(), DOOR_ID)
                                .data())
                        .proposal())
                .isEqualTo("Він пішов.");
    }

    @Test
    void segment_unknownId_isValidation() {
        // an id the project does not hold is refused, not thrown
        final Desk desk = ReviewFixtures.epub(tempDir);

        assertThat(desk.queries().segment(desk.projectId(), "ch99.xhtml:0").error())
                .isNotNull()
                .extracting(error -> error.code())
                .isEqualTo(ErrorCode.validation);
    }

    @Test
    void queue_projectWithNoOpenBook_isValidation() {
        // views need the opened document for source text and locators
        final Desk desk = ReviewFixtures.epub(tempDir);

        final Result<List<SegmentView>> result =
                desk.queriesWithoutOpenBook().queue(desk.projectId(), ReviewFilter.ALL_FLAGGED);

        assertThat(result.error()).isNotNull().extracting(error -> error.code()).isEqualTo(ErrorCode.validation);
    }

    private Desk flaggedThree() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, ReviewFixtures.MONSTER_TARGET);
        update(desk, FLAGGED_ID, record -> judged(record, 0.58));
        flag(desk, NAMES_ID, "Гейл пішов.");
        update(desk, NAMES_ID, record -> record.withFindings(List.of(finding("glossary", "glossary"))));
        flag(desk, SCRIPT_ID, "Дім.");
        update(desk, SCRIPT_ID, record -> record.withFindings(List.of(finding("language", "script"))));
        return desk;
    }

    private static SegmentRecord judged(final SegmentRecord record, final double score) {
        return new SegmentRecord(
                record.projectId(),
                record.segmentId(),
                record.unitId(),
                record.ord(),
                record.kind(),
                record.status(),
                record.machineTarget(),
                record.maskedMachineTarget(),
                record.userTarget(),
                record.maskedUserTarget(),
                record.confidence(),
                score,
                record.findings(),
                record.path(),
                record.repairRounds(),
                record.reviewed(),
                record.context());
    }

    private static List<SegmentView> queue(final Desk desk, final ReviewFilter filter) {
        return Objects.requireNonNull(
                desk.queries().queue(desk.projectId(), filter).data());
    }
}
