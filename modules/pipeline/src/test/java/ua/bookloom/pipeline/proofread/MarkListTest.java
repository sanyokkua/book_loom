package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.eval.ExportedBooks;

/** The mark list's doubts, read from a decided Russian-to-Ukrainian book as the Proofread stage reads it. */
class MarkListTest {

    private static final DocumentPort DOCUMENTS =
            Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    private static final BookBrief BRIEF = BookBrief.defaults("ru").withLanguages("ru", "uk");

    @TempDir
    private Path tempDir;

    // The audit's negation doubt, stored on the record, is an audit mark that names the check and the count.
    @Test
    void markList_negationLost_listedWithReason() {
        final Book book = book(
                "Я дошла до Зотова только к ночи и никого не встретила на тропе.",
                "Я дійшла до Зотова лише під ніч і зустріла когось на стежці.");
        final List<SegmentRecord> audited = audited(book);

        final List<Mark> marks = MarkList.of(book.marks(List.of()), audited, List.of());

        assertThat(marks).singleElement().satisfies(mark -> {
            assertThat(mark.tier()).isEqualTo(Mark.Tier.DOUBTFUL);
            assertThat(mark.reasons())
                    .anyMatch(reason -> reason.startsWith("audit negation: The source holds 2 more negation words"));
        });
    }

    // A map name the source holds whose target forms the translation lacks is listed with that reason.
    @Test
    void markList_nameFormMissing_listed() {
        final Book book = book("Ивар открыл дверь.", "Він відчинив двері.");

        final List<Mark> marks = MarkList.of(book.marks(List.of(ivar())), book.records(), List.of());

        assertThat(marks)
                .containsExactly(new Mark(
                        book.records().getFirst().segmentId(),
                        Mark.Tier.DOUBTFUL,
                        List.of("name form missing: Ивар → Івар")));
    }

    // A declined form of the name is one of its target forms, so nothing is listed.
    @Test
    void markList_nameDeclined_notListed() {
        final Book book = book("Дверь открыли для Ивара.", "Двері відчинили для Івара.");

        assertThat(MarkList.of(book.marks(List.of(ivar())), book.records(), List.of()))
                .isEmpty();
    }

    private static GlossaryEntry ivar() {
        return GlossaryEntry.of("p:ивар", "p", "Ивар", "Івар", TermType.CHARACTER, Gender.MALE, false);
    }

    private Book book(final String source, final String target) {
        final Document original = ExportedBooks.open(DOCUMENTS, TestBooks.markdown(tempDir.resolve("ch05.md"), source));
        final Document written =
                ExportedBooks.open(DOCUMENTS, TestBooks.markdown(tempDir.resolve("ch05-uk.md"), target));
        return new Book(original, ExportedBooks.recordsOf(original, written));
    }

    // What the Proofread stage stores before it lists: the audit's findings on each record.
    private static List<SegmentRecord> audited(final Book book) {
        final Map<String, List<QaFinding>> doubted = FinalAudit.scan(
                new FinalAudit.Book(BRIEF, book.document(), List.of(), WordValidator.none()), book.records());
        return book.records().stream()
                .map(record -> record.withFindings(doubted.getOrDefault(record.segmentId(), List.of())))
                .toList();
    }

    private record Book(Document document, List<SegmentRecord> records) {

        MarkList.Book marks(final List<GlossaryEntry> glossary) {
            return new MarkList.Book(BRIEF, document, glossary, List.of());
        }
    }
}
