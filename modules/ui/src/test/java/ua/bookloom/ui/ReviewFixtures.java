package ua.bookloom.ui;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;

/** The segments the review panel tests list, as the desk would describe them. */
public final class ReviewFixtures {

    public static final String MASKED_TARGET = "Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері.";

    private ReviewFixtures() {}

    public static SegmentView view(
            final String id,
            final String locator,
            final SegmentStatus status,
            final List<QaFinding> findings,
            final @Nullable Double judgeScore,
            final @Nullable ContextSnapshot context) {
        return new SegmentView(
                id,
                locator,
                SegmentKind.PARAGRAPH,
                status,
                "Gale opened the ⟦g0⟧old⟦g1⟧ door.",
                "Gale opened the old door.",
                MASKED_TARGET,
                null,
                null,
                findings,
                judgeScore,
                SegmentPath.DRAFT,
                false,
                context,
                null);
    }

    /** Every part of the context a draft can see: brief, two names, a preceding target and a summary. */
    public static ContextSnapshot fullContext() {
        return new ContextSnapshot(
                List.of("Раніше в тексті."),
                List.of(
                        new SnapshotTerm("Gale", "Гейл", TermType.CHARACTER, Gender.MALE, true),
                        new SnapshotTerm("Baker Street", null, TermType.PLACE, Gender.NEUTER, false)),
                List.of(),
                "Gale returns home.",
                "Formal, past tense.");
    }

    public static SegmentView lowScore() {
        return view("ch05.xhtml:11", "ch5 · p12", SegmentStatus.FLAGGED, List.of(), 0.58, fullContext());
    }

    /** The low-scoring segment with the judge's finding on it, as the findings list shows it. */
    public static SegmentView lowScoreWithFinding() {
        return view(
                "ch05.xhtml:11",
                "ch5 · p12",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("judge", Severity.MEDIUM, "Reads stiffly", "judge")),
                0.58,
                fullContext());
    }

    public static SegmentView nameIssue() {
        return view(
                "ch07.xhtml:39",
                "ch7 · p40",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("glossary", Severity.MEDIUM, "Name changed", "glossary")),
                null,
                null);
    }

    public static SegmentView wrongLanguage() {
        return view(
                "ch09.xhtml:2",
                "ch9 · p03",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("language", Severity.MEDIUM, "Looks Russian", "script")),
                null,
                null);
    }

    /** A flagged segment the reviewer fixed in place with one gender edit and could not fix in another place. */
    public static SegmentView withAppliedEdit() {
        return view(
                "ch11.xhtml:8",
                "ch11 · p09",
                SegmentStatus.FLAGGED,
                List.of(
                        new AppliedEdit("gender", "Вона втомився", "Вона втомилася").toFinding(),
                        new AppliedEdit("invented-word", "абракадабра ", "").toFinding()),
                null,
                null);
    }

    public static SegmentView machineTargetOnly() {
        return view("ch03.xhtml:2", "ch3 · p02", SegmentStatus.FLAGGED, List.of(), null, null);
    }

    /** A flagged segment holding a backward-revision proposal beside the machine target. */
    public static SegmentView withProposal() {
        final SegmentView base = machineTargetOnly();
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                base.maskedMachineTarget(),
                null,
                null,
                base.findings(),
                base.judgeScore(),
                base.path(),
                base.reviewed(),
                null,
                "Він пішов.");
    }

    public static SegmentView accepted() {
        return view("ch02.xhtml:4", "ch2 · p04", SegmentStatus.ACCEPTED, List.of(), null, null);
    }

    /** A flagged segment whose texts and findings are long enough to wrap in every pane. */
    public static SegmentView longSegment() {
        final String text = "She had lost her mother, and the poor girl wept as she followed the coffin. ".repeat(6);
        return new SegmentView(
                "ch11.xhtml:7",
                "ch11 · p08",
                SegmentKind.PARAGRAPH,
                SegmentStatus.FLAGGED,
                text,
                text,
                text,
                null,
                null,
                List.of(new QaFinding("omission", Severity.HIGH, text, "judge")),
                0.41,
                SegmentPath.DRAFT,
                false,
                fullContext(),
                null);
    }
}
