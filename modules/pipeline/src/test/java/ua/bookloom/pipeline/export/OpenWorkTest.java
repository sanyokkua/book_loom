package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.pipeline.ExportOpenWork;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.proofread.MarkList;

/** What the Export screen's banners say still waits, read from an invented book whose character's gender arrived. */
class OpenWorkTest {

    private static final String PROJECT = "p";
    private static final BookBrief BRIEF = BookBrief.defaults("en").withLanguages("en", "uk");
    private static final Document BOOK =
            IndexTestBooks.document(IndexTestBooks.unit("body", UnitRole.BODY, "The door was shut.", "It rained."));

    // A gender the person gave after the run reaches the segment that waited on it: the banner names the fact.
    @Test
    void arrivedGender_namesTheFact() {
        final GlossaryEntry orrin =
                GlossaryEntry.of("e:orrin", PROJECT, "Orrin", "Оррін", TermType.CHARACTER, Gender.FEMALE, false);
        final Deferral waited =
                new Deferral("d1", PROJECT, "body:1", DeferralReason.GENDER_UNKNOWN, "Orrin", null, null, null);

        final ExportOpenWork work = OpenWork.of(
                new MarkList.Book(BRIEF, BOOK, List.of(orrin), List.of()),
                List.of(decided(0, "Двері були зачинені."), decided(1, "Ішов дощ.")),
                List.of(waited));

        assertThat(work).isEqualTo(new ExportOpenWork(1, 1, List.of("Оррін")));
    }

    // A book opened before its target language is chosen has no open work yet, and reading it is no failure.
    @Test
    void of_targetLanguageNotChosen_returnsNone() {
        final ExportOpenWork work = OpenWork.of(
                new MarkList.Book(BookBrief.defaults("en"), BOOK, List.of(), List.of()), List.of(), List.of());

        assertThat(work).isEqualTo(ExportOpenWork.NONE);
    }

    private static SegmentRecord decided(final int position, final String text) {
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
