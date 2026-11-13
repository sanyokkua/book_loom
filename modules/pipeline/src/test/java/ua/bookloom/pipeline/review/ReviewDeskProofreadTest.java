package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;
import static ua.bookloom.pipeline.review.ReviewFixtures.stored;
import static ua.bookloom.pipeline.review.ReviewFixtures.update;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * The desk over the Proofread stage's verdicts (design D15 "Visible"): Undo proofread puts back the text, the status
 * and the findings a kept rewrite replaced and refuses a segment it cannot undo, the Changed by proofread and Waiting
 * on a gender lists and their counts, and at Fast the segment a gender now known disagrees with in Worth a second look.
 * Invented en→uk text over a real imported EPUB and the in-memory stores.
 */
class ReviewDeskProofreadTest {

    private static final String KEPT_ID = "ch04.xhtml:7";
    private static final String FLAGGED_ID = "ch05.xhtml:14";
    private static final String EDITED_ID = "ch02.xhtml:4";
    private static final String GENDER_ID = "ch05.xhtml:40";
    private static final String BEFORE = "Тарн прийде завтра.";
    private static final String AFTER = "Тарн прийшов завтра.";
    private static final QaFinding TYPOGRAPHY =
            new QaFinding("fluency", Severity.LOW, "Typography normalised: 1 apostrophe.", "normalised");
    private static final QaFinding ALPHABET = new QaFinding("language", Severity.HIGH, "кобылу", "alphabet");

    @TempDir
    private Path tempDir;

    private static ProofreadVerdict kept(final String before, final SegmentStatus status, final List<QaFinding> held) {
        return new ProofreadVerdict("r1:fp", ProofreadOutcome.KEPT, "", before, status, before, held);
    }

    private static void keptRewrite(
            final Desk desk, final String id, final ProofreadVerdict verdict, final String after) {
        update(
                desk,
                id,
                record -> record.withStatus(SegmentStatus.REVISED)
                        .withMachineTarget(after, after)
                        .withFindings(List.of())
                        .withProofread(verdict));
    }

    @Test
    void undoProofread_keptRewrite_restoresBefore() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, KEPT_ID, BEFORE);
        keptRewrite(desk, KEPT_ID, kept(BEFORE, SegmentStatus.ACCEPTED, List.of()), AFTER);

        final Result<SegmentRecord> undone =
                desk.reviewDesk(ReviewMode.ASSISTED).undoProofread(desk.projectId(), KEPT_ID);

        assertThat(undone.isOk()).isTrue();
        final SegmentRecord now = stored(desk, KEPT_ID);
        assertThat(now.machineTarget()).isEqualTo(BEFORE);
        assertThat(now.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(now.proofread()).isNull();
        assertThat(now.reviewed()).isTrue();
    }

    // IF Undo proofread accepted a text a check had blocked, THEN the person would lose the finding that flagged it.
    @Test
    void undoProofread_flaggedSegmentRewriteKept_returnsToFlagged() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        update(
                desk,
                FLAGGED_ID,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget("Він осідлав кобылу.", "Він осідлав кобылу.")
                        .withFindings(List.of(ALPHABET)));
        keptRewrite(
                desk,
                FLAGGED_ID,
                kept("Він осідлав кобылу.", SegmentStatus.FLAGGED, List.of(ALPHABET)),
                "Він осідлав кобилу.");

        desk.reviewDesk(ReviewMode.ASSISTED).undoProofread(desk.projectId(), FLAGGED_ID);

        final SegmentRecord now = stored(desk, FLAGGED_ID);
        assertThat(now.machineTarget()).isEqualTo("Він осідлав кобылу.");
        assertThat(now.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(now.findings()).containsExactly(ALPHABET);
    }

    // IF Undo proofread touched the person's own edit, THEN the stage's undo would throw away their wording.
    @Test
    void undoProofread_personEdit_refusedValidation() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, EDITED_ID, "Дощ ущух лише надвечір.");
        Objects.requireNonNull(desk.actions()
                .saveEdit(desk.projectId(), EDITED_ID, "Дощ ущух аж надвечір.")
                .data());

        final Result<SegmentRecord> refused =
                desk.reviewDesk(ReviewMode.ASSISTED).undoProofread(desk.projectId(), EDITED_ID);

        assertThat(refused.error()).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, EDITED_ID).userTarget()).isEqualTo("Дощ ущух аж надвечір.");
        assertThat(stored(desk, EDITED_ID).status()).isEqualTo(SegmentStatus.REVISED);
    }

    // IF the lists of the Proofread stage's changes and of the segments waiting on a gender were not counted, THEN
    // their
    // filters could not say how much each holds; an undone rewrite leaves Changed by proofread.
    @Test
    void lists_keptProposedAndWaiting_areListedCountedAndUndoneRewriteLeaves() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, KEPT_ID, BEFORE);
        keptRewrite(desk, KEPT_ID, kept(BEFORE, SegmentStatus.ACCEPTED, List.of()), AFTER);
        accept(desk, EDITED_ID, "Вона пішла.");
        update(
                desk,
                EDITED_ID,
                record -> record.withProofread(
                        new ProofreadVerdict("r1:fp", ProofreadOutcome.PROPOSED, "the person's edit stays")));
        add(desk, proposal(desk, EDITED_ID, "Вона вийшла."));
        accept(desk, GENDER_ID, "Ірвін прийшов.");
        add(desk, deferral(desk, GENDER_ID, "Irvin"));
        final ReviewDeskImpl port = desk.reviewDesk(ReviewMode.ASSISTED);

        assertThat(ids(port, desk, ReviewFilter.CHANGED_BY_PROOFREAD)).containsExactly(EDITED_ID, KEPT_ID);
        assertThat(ids(port, desk, ReviewFilter.WAITING_ON_GENDER)).containsExactly(GENDER_ID);
        assertThat(port.queue(desk.projectId(), ReviewFilter.CHANGED_BY_PROOFREAD)
                        .data())
                .filteredOn(view -> view.segmentId().equals(KEPT_ID))
                .singleElement()
                .extracting(SegmentView::proofread)
                .extracting(ProofreadVerdict::outcome)
                .isEqualTo(ProofreadOutcome.KEPT);
        final ReviewCounts counts =
                Objects.requireNonNull(port.counts(desk.projectId()).data());
        assertThat(counts.changedByProofread()).isEqualTo(2);
        assertThat(counts.waitingOnGender()).isEqualTo(1);

        port.undoProofread(desk.projectId(), KEPT_ID);

        assertThat(ids(port, desk, ReviewFilter.CHANGED_BY_PROOFREAD)).containsExactly(EDITED_ID);
    }

    // At Fast no window reads the segment, so a gender the person set afterwards that the text disagrees with is left
    // on
    // the desk with its reason; a segment that agrees is not listed. The Ukrainian agreement check holds a woman's name
    // to its verb, so the gender that arrives here is female.
    @Test
    void secondLook_fastGenderNowKnownDisagrees_listsTheSegmentWithItsReason() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        Objects.requireNonNull(desk.glossary()
                .add(GlossaryEntry.of(
                        GlossaryIds.of(desk.projectId(), "Korin Ashby"),
                        desk.projectId(),
                        "Korin Ashby",
                        "Корін Ешбі",
                        TermType.CHARACTER,
                        Gender.FEMALE,
                        false))
                .data());
        accept(desk, GENDER_ID, "Корін Ешбі сказав, що прийде.");
        accept(desk, KEPT_ID, "Корін Ешбі сказала, що прийде.");
        add(desk, deferral(desk, GENDER_ID, "Korin Ashby"));
        add(desk, deferral(desk, KEPT_ID, "Korin Ashby"));

        final List<SegmentView> listed = Objects.requireNonNull(desk.reviewDesk(ReviewMode.ASSISTED)
                .queue(desk.projectId(), ReviewFilter.SUSPICIOUS)
                .data());

        assertThat(listed).extracting(SegmentView::segmentId).containsExactly(GENDER_ID);
        assertThat(listed.getFirst().findings())
                .extracting(QaFinding::note)
                .contains("gender now known: Корін Ешбі — female");
    }

    // At Fast the Proofread stage reads no window, so a note-level finding left on an accepted segment is a doubtful
    // mark the export counts as waiting on the person; the desk lists it under Worth a second look and counts it too.
    @Test
    void secondLook_fastNoteLevelMark_listsAndCountsWhatTheExportCounts() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, KEPT_ID, BEFORE);
        update(desk, KEPT_ID, record -> record.withFindings(List.of(TYPOGRAPHY)));
        final ReviewDeskImpl port = desk.reviewDesk(ReviewMode.ASSISTED);

        assertThat(ids(port, desk, ReviewFilter.SUSPICIOUS)).containsExactly(KEPT_ID);
        assertThat(Objects.requireNonNull(port.counts(desk.projectId()).data()).suspicious())
                .isEqualTo(1);
    }

    // IF Changed by proofread went on listing a segment once the person applied its proposal or wrote over a kept
    // rewrite, THEN the list would hold work already done.
    @Test
    void changedByProofread_proposalAppliedAndEditOverAKeptRewrite_leaveTheList() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, KEPT_ID, BEFORE);
        keptRewrite(desk, KEPT_ID, kept(BEFORE, SegmentStatus.ACCEPTED, List.of()), AFTER);
        final ReviewDeskImpl port = desk.reviewDesk(ReviewMode.ASSISTED);
        accept(desk, EDITED_ID, "Вона пішла.");
        Objects.requireNonNull(
                port.saveEdit(desk.projectId(), EDITED_ID, "Вона пішла геть.").data());
        update(
                desk,
                EDITED_ID,
                record -> record.withProofread(
                        new ProofreadVerdict("r1:fp", ProofreadOutcome.PROPOSED, "the person's edit stays")));
        add(desk, proposal(desk, EDITED_ID, "Вона вийшла геть."));
        assertThat(ids(port, desk, ReviewFilter.CHANGED_BY_PROOFREAD)).containsExactly(EDITED_ID, KEPT_ID);

        Objects.requireNonNull(port.acceptProposal(desk.projectId(), EDITED_ID).data());
        Objects.requireNonNull(port.saveEdit(desk.projectId(), KEPT_ID, "Тарн прийшов аж завтра.")
                .data());

        assertThat(ids(port, desk, ReviewFilter.CHANGED_BY_PROOFREAD)).isEmpty();
        assertThat(Objects.requireNonNull(port.counts(desk.projectId()).data()).changedByProofread())
                .isZero();
    }

    private static Deferral proposal(final Desk desk, final String segmentId, final String text) {
        return new Deferral(
                desk.projectId() + ":" + segmentId + ":PROOFREAD:r1:fp",
                desk.projectId(),
                segmentId,
                DeferralReason.PROOFREAD,
                null,
                null,
                text,
                text);
    }

    private static List<String> ids(final ReviewDeskImpl port, final Desk desk, final ReviewFilter filter) {
        return Objects.requireNonNull(port.queue(desk.projectId(), filter).data()).stream()
                .map(SegmentView::segmentId)
                .toList();
    }

    private static Deferral deferral(final Desk desk, final String segmentId, final String term) {
        return new Deferral(
                desk.projectId() + ":" + segmentId + ":gender",
                desk.projectId(),
                segmentId,
                DeferralReason.GENDER_UNKNOWN,
                term,
                null,
                null,
                null);
    }

    private static void add(final Desk desk, final Deferral deferral) {
        Objects.requireNonNull(desk.deferrals().add(deferral).data());
    }
}
