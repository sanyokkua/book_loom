package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;

/** The review desk's lists: six segments to review, three worth a second look, and the kept-as-source ones. */
public final class ReviewDeskFixtures {

    /** The segment a desk that opens by itself shows first. */
    public static final String FIRST_ID = "ch02.xhtml:5";

    private ReviewDeskFixtures() {}

    public static SegmentView firstToReview() {
        return ReviewFixtures.view(
                FIRST_ID,
                "ch2 · p05",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("glossary", Severity.MEDIUM, "Name changed", "glossary")),
                null,
                null);
    }

    /** A segment drafted alone: its context has no batch prompt of its own. */
    public static SegmentView draftedAlone() {
        return ReviewFixtures.view(
                "ch03.xhtml:1", "ch3 · p01", SegmentStatus.FLAGGED, List.of(), 0.5, ReviewFixtures.fullContext());
    }

    /** A segment adopted from a batch reply: its context holds the sections that batch request really sent. */
    public static SegmentView batchAdopted() {
        final ContextSnapshot context = new ContextSnapshot(
                List.of("Перший раніший рядок.", "Другий раніший рядок."),
                List.of(new SnapshotTerm("Velk", "Велк", TermType.CHARACTER, Gender.MALE, true, false, null)),
                List.of(),
                null,
                "",
                List.of(),
                List.of(),
                List.of(
                        new PromptSection(
                                "glossaryTerms", "[Glossary]", PromptSection.Origin.USER, List.of("Velk → Велк")),
                        new PromptSection(
                                "precedingPairs",
                                "[Earlier]",
                                PromptSection.Origin.USER,
                                List.of("one line → перший раніший рядок", "two lines → другий раніший рядок"))));
        return ReviewFixtures.view("ch02.xhtml:6", "ch2 · p06", SegmentStatus.FLAGGED, List.of(), null, context);
    }

    /** Six flagged segments in document order, the first being {@link #firstToReview()}. */
    public static List<SegmentView> sixToReview() {
        return List.of(
                firstToReview(),
                draftedAlone(),
                ReviewFixtures.lowScore(),
                ReviewFixtures.nameIssue(),
                ReviewFixtures.wrongLanguage(),
                batchAdopted());
    }

    /** The first four of {@link #sixToReview()}. */
    public static List<SegmentView> fourToReview() {
        return sixToReview().subList(0, 4);
    }

    /** Three accepted segments the final audit still doubts, the first being {@code ch2 · p04}. */
    public static List<SegmentView> worthASecondLook() {
        return List.of(
                ReviewFixtures.accepted(),
                ReviewFixtures.view("ch02.xhtml:8", "ch2 · p08", SegmentStatus.ACCEPTED, List.of(), null, null),
                ReviewFixtures.view("ch04.xhtml:2", "ch4 · p02", SegmentStatus.ACCEPTED, List.of(), null, null));
    }

    /** {@code count} segments kept in the original language, {@code nav · 01} onward. */
    public static List<SegmentView> keptAsSource(final int count) {
        final List<SegmentView> kept = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final String number = String.format("%02d", i);
            kept.add(new SegmentView(
                    "nav" + number,
                    "nav · " + number,
                    SegmentKind.PARAGRAPH,
                    SegmentStatus.ACCEPTED,
                    "Contents",
                    "Contents",
                    "Contents",
                    null,
                    null,
                    List.of(),
                    null,
                    SegmentPath.SOURCE_KEPT,
                    false,
                    null,
                    null));
        }
        return kept;
    }

    /** {@code count} accepted segments {@code ch1 · p01} onward, for a long All segments list. */
    public static List<SegmentView> acceptedRows(final int count) {
        final List<SegmentView> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(ReviewFixtures.view(
                    "ch01.xhtml:" + i, "ch1 · p" + i, SegmentStatus.ACCEPTED, List.of(), null, null));
        }
        return rows;
    }

    public static ReviewCounts counts(final int flagged, final int suspicious) {
        return new ReviewCounts(100, 90, 0, flagged, 0, 0, 0, 0, 0, suspicious);
    }
}
