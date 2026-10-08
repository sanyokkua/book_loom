package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.review.ReviewFixtures;

/** A neighbour check whose reply writes the quote marks as control codes: mapped when clear, named when not. */
class ConsistencyPassControlCodesTest {

    private static final String CONSISTENCY = "consistency";
    private static final String BEFORE = "«Сем зустрів Хейла.»";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(BEFORE, BEFORE)
                        .withPath(SegmentPath.REPAIRED));
    }

    // IF the codes were refused, THEN the fix of a dialogue paragraph would be wasted as on the Oct 8 run.
    @Test
    void run_replyWithQuoteCodes_isMappedAndStored() {
        book.model().answerTo(CONSISTENCY, reply("\\u001eСем зустрів Гейла.\\u001d"));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo("«Сем зустрів Гейла.»");
        assertThat(report.neighbourFixes()).isEqualTo(1);
        assertThat(report.checks().refused()).isEmpty();
    }

    // IF the refusal still said "unreadable", THEN the cause of eight wasted calls stayed hidden.
    @Test
    void run_replyWithAnUnpairableCode_isRefusedNamingTheControlCodes() {
        book.model().answerTo(CONSISTENCY, reply("\\u001eСем зустрів Гейла."));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
        assertThat(report.checks().refused()).containsExactly(entry("control_chars", 1));
    }
}
