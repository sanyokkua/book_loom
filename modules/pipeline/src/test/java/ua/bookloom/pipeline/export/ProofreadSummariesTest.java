package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.proofread.MarkList;

/** The proofread summary an export keeps, read from invented stored records, deferrals and a glossary. */
class ProofreadSummariesTest {

    private static final String PROJECT = "p";
    private static final BookBrief BRIEF = BookBrief.defaults("en").withLanguages("en", "uk");
    private static final int LINES = 15;
    private static final Document BOOK = IndexTestBooks.document(IndexTestBooks.unit(
            "body",
            UnitRole.BODY,
            IntStream.range(0, LINES).mapToObj(n -> "Line " + n + " goes on.").toArray(String[]::new)));
    private static final GlossaryEntry ORRIN =
            GlossaryEntry.of("e:orrin", PROJECT, "Orrin", "Оррін", TermType.CHARACTER, Gender.UNKNOWN, false);
    // A repair-level finding: the check's own name at medium severity.
    private static final QaFinding ADDRESS_MIX =
            new QaFinding("language", Severity.MEDIUM, "ти and Ви mixed", "address-mix");

    // Twelve windows read; a flagged segment that also waits on a gender, an unread proofread mark and a gender
    // deferral are three waiting segments, and a read segment with a mark no longer waits.
    @Test
    void summary_twelveReadThreeWaiting_countsFlaggedMarkAndDeferralOnce() {
        final List<SegmentRecord> records = new ArrayList<>();
        IntStream.range(0, 12).forEach(n -> records.add(read(n, ProofreadOutcome.UNCHANGED)));
        records.set(5, records.get(5).withFindings(List.of(ADDRESS_MIX)));
        records.add(decided(12).withStatus(SegmentStatus.FLAGGED));
        records.add(decided(13).withFindings(List.of(ADDRESS_MIX)));
        records.add(decided(14));
        final List<Deferral> open = List.of(waitingOnOrrin("d1", 12), waitingOnOrrin("d2", 14));

        final ProofreadSummary summary = ProofreadSummaries.of(book(), records, open);

        assertThat(summary).isEqualTo(new ProofreadSummary(12, 3, List.of(new GenderWait("Orrin", 2)), 0, 2));
    }

    // A rewrite the never-worse gate refused left the text as it was, so its mark still waits.
    @Test
    void summary_rejectedRewrite_keepsItsMark() {
        final List<SegmentRecord> records = List.of(
                read(0, ProofreadOutcome.REJECTED).withFindings(List.of(ADDRESS_MIX)), read(1, ProofreadOutcome.KEPT));

        final ProofreadSummary summary = ProofreadSummaries.of(book(), records, List.of());

        assertThat(summary).isEqualTo(new ProofreadSummary(2, 1));
    }

    private static MarkList.Book book() {
        return new MarkList.Book(BRIEF, BOOK, List.of(ORRIN), List.of());
    }

    private static Deferral waitingOnOrrin(final String id, final int position) {
        return new Deferral(id, PROJECT, "body:" + position, DeferralReason.GENDER_UNKNOWN, "Orrin", null, null, null);
    }

    private static SegmentRecord read(final int position, final ProofreadOutcome outcome) {
        return decided(position).withProofread(new ProofreadVerdict("r1:" + position, outcome, ""));
    }

    private static SegmentRecord decided(final int position) {
        final String text = "Рядок " + position + " іде далі.";
        return new SegmentRecord(
                PROJECT,
                "body:" + position,
                "body",
                position,
                SegmentKind.PARAGRAPH,
                SegmentStatus.ACCEPTED,
                text,
                text,
                null,
                null,
                1.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                false,
                null);
    }
}
