package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.Decision;

/** What a decision becomes in storage, and what a stored flag reads back as. */
class OutcomeRecordsTest {

    // Storing only the plain target would leave the review editor without the masked form it shows and saves.
    @Test
    void decided_acceptedDecision_storesBothTargetFormsAndTheDraftPath() {
        final Segment segment = segment().withDecision(SegmentStatus.ACCEPTED, "HE *OLD* DOOR.");
        final Decision decision = new Decision(segment, null, "HE ⟦g0⟧OLD⟦g1⟧ DOOR.");

        final SegmentRecord record = OutcomeRecords.decided(pending(), decision);

        assertThat(record)
                .extracting(
                        SegmentRecord::status,
                        SegmentRecord::machineTarget,
                        SegmentRecord::maskedMachineTarget,
                        SegmentRecord::userTarget,
                        SegmentRecord::path,
                        SegmentRecord::findings)
                .containsExactly(
                        SegmentStatus.ACCEPTED,
                        "HE *OLD* DOOR.",
                        "HE ⟦g0⟧OLD⟦g1⟧ DOOR.",
                        null,
                        SegmentPath.DRAFT,
                        List.of());
    }

    // A flagged draft has no target to keep, and its reason must survive as the finding the review desk shows.
    @Test
    void decided_flaggedDecision_storesItsReasonAsAHighReplyFinding() {
        final AppError reason = AppError.of(ErrorCode.contextWindow, "Too long", "The segment exceeds the window.");
        final Segment segment = segment().withDecision(SegmentStatus.FLAGGED, null);

        final SegmentRecord record = OutcomeRecords.decided(pending(), new Decision(segment, reason, null));

        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.machineTarget()).isNull();
        assertThat(record.findings())
                .containsExactly(
                        new QaFinding("contextWindow", Severity.HIGH, "The segment exceeds the window.", "reply"));
    }

    // Reading a different code back would report a flagged segment under a reason the model never gave.
    @Test
    void reportCode_contextWindowFlag_readsBackContextWindow() {
        final AppError reason = AppError.of(ErrorCode.contextWindow, "Too long", "The segment exceeds the window.");
        final Segment segment = segment().withDecision(SegmentStatus.FLAGGED, null);
        final SegmentRecord flagged = OutcomeRecords.decided(pending(), new Decision(segment, reason, null));

        assertThat(OutcomeRecords.reportCode(flagged)).isEqualTo(ErrorCode.contextWindow);
    }

    // A segment flagged after repair rounds has no reply finding, and the report names it validation.
    @Test
    void reportCode_onlyAMarkupFinding_readsBackValidation() {
        final SegmentRecord flagged = pending()
                .withStatus(SegmentStatus.FLAGGED)
                .withFindings(List.of(new QaFinding("markup", Severity.HIGH, "A token is missing.", "placeholder")));

        assertThat(OutcomeRecords.reportCode(flagged)).isEqualTo(ErrorCode.validation);
    }

    private static SegmentRecord pending() {
        return new SegmentRecord(
                "p1",
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                SegmentStatus.PENDING,
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                "He *old* door.",
                "He ⟦g0⟧old⟦g1⟧ door.",
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, 4),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
