package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.QuoteStyle;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.audit.AuditFindings;
import ua.bookloom.pipeline.run.BriefKeyRecheck;

/** Changing a Brief key re-checks the stored text by code; nothing is drafted again, so no model is ever asked. */
class BriefKeyRecheckTest {

    private static final String STRAIGHT_TARGET = "Мама купила м'яту на ринку сьогодні вранці.";
    private static final String CLEAN_TARGET = "Мама купила яблука на ринку сьогодні вранці.";
    private static final int PARAGRAPHS = 40;
    private static final int WITH_APOSTROPHE = 12;
    private static final QuoteStyle CURLY = new QuoteStyle("“", "”", "‘", "’");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject decidedBook(final String quoted) {
        final String text = String.join(
                "\n\n",
                IntStream.range(0, PARAGRAPHS)
                        .mapToObj(i -> "Mum bought mint at the market on day " + i + " " + quoted + ".")
                        .toList());
        final TestProject book = project(TestBooks.markdown(tempDir.resolve("Book.md"), text), brief("en", "uk"));
        final List<SegmentRecord> records = recordsOf(book);
        for (int i = 0; i < records.size(); i++) {
            final String target = i < WITH_APOSTROPHE ? STRAIGHT_TARGET : CLEAN_TARGET;
            decide(book, records.get(i).segmentId(), target);
        }
        return book;
    }

    private static List<SegmentRecord> recordsOf(final TestProject book) {
        return Objects.requireNonNull(book.stores().segments().all(book.id()).data());
    }

    private static void decide(final TestProject book, final String segmentId, final String target) {
        book.stores()
                .segments()
                .update(
                        book.id(),
                        segmentId,
                        record -> record.withMachineTarget(target, target).withStatus(SegmentStatus.ACCEPTED));
    }

    private static BriefKeyRecheck recheck(final TestProject book) {
        final var stores = book.stores();
        return new BriefKeyRecheck(stores.segments(), stores.openProjects());
    }

    private static int run(final TestProject book, final BookBrief before, final BookBrief after) {
        return Objects.requireNonNull(
                recheck(book).recheck(book.id(), before, after).data());
    }

    private static List<String> flaggedIds(final TestProject book) {
        return recordsOf(book).stream()
                .filter(record -> record.findings().stream().anyMatch(AuditFindings::isAudit))
                .map(SegmentRecord::segmentId)
                .toList();
    }

    @Test
    void recheck_apostropheChanged_flagsOldStraightApostrophe() {
        final TestProject book = decidedBook("said");
        final BookBrief before = brief("en", "uk").withApostrophe("'");
        final BookBrief after = brief("en", "uk").withApostrophe("’");

        final int flagged = run(book, before, after);

        assertThat(flagged).isEqualTo(WITH_APOSTROPHE);
        assertThat(flaggedIds(book)).hasSize(WITH_APOSTROPHE);
        assertThat(recordsOf(book)).allMatch(record -> record.status() == SegmentStatus.ACCEPTED);
        assertThat(recordsOf(book).getFirst().machineTarget()).isEqualTo(STRAIGHT_TARGET);
    }

    @Test
    void recheck_quoteStyleChanged_flagsOldQuoteMarks() {
        final TestProject book = decidedBook("said");
        final String quoted = "Він сказав \"іди\" зараз.";
        decide(book, recordsOf(book).get(5).segmentId(), quoted);

        final int flagged = run(book, brief("en", "uk"), brief("en", "uk").withQuoteStyle(CURLY));

        assertThat(flagged).isEqualTo(1);
        assertThat(flaggedIds(book)).containsExactly(recordsOf(book).get(5).segmentId());
    }

    @Test
    void recheck_nothingDecided_noWork() {
        final TestProject book = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Mum bought mint.\n\nShe left."), brief("en", "uk"));

        final int flagged = run(book, brief("en", "uk"), brief("en", "uk").withApostrophe("’"));

        assertThat(flagged).isZero();
        assertThat(flaggedIds(book)).isEmpty();
    }

    @Test
    void recheck_reviewedSegment_untouched() {
        final TestProject book = decidedBook("said");
        final String first = recordsOf(book).getFirst().segmentId();
        book.stores().segments().update(book.id(), first, record -> record.withReviewed(true));

        final int flagged = run(
                book, brief("en", "uk").withApostrophe("'"), brief("en", "uk").withApostrophe("’"));

        assertThat(flagged).isEqualTo(WITH_APOSTROPHE - 1);
        assertThat(flaggedIds(book)).doesNotContain(first);
    }

    @Test
    void recheck_sameKeys_noWork() {
        final TestProject book = decidedBook("said");
        final BookBrief same = brief("en", "uk").withApostrophe("’");

        assertThat(run(book, same, same.withGenre("Fantasy"))).isZero();
        assertThat(flaggedIds(book)).isEmpty();
    }

    @Test
    void recheck_runsTwice_secondFlagsNothingNew() {
        final TestProject book = decidedBook("said");
        final BookBrief before = brief("en", "uk").withApostrophe("'");
        final BookBrief after = brief("en", "uk").withApostrophe("’");
        run(book, before, after);

        run(book, before, after);

        assertThat(recordsOf(book).stream()
                        .mapToLong(record -> record.findings().stream()
                                .filter(AuditFindings::isAudit)
                                .count())
                        .max())
                .hasValue(1L);
        assertThat(flaggedIds(book)).hasSize(WITH_APOSTROPHE);
    }

    @Test
    void recheck_defaultAddressChanged_flagsNothingYet() {
        final TestProject book = decidedBook("said");

        final int flagged = run(
                book,
                brief("en", "uk"),
                brief("en", "uk")
                        .withDefaultAddress(new Address(AddressForm.FORMAL, AddressForm.INFORMAL))
                        .withConventions(Map.of("k", "v")));

        assertThat(flagged).isZero();
        assertThat(flaggedIds(book)).isEmpty();
    }
}
