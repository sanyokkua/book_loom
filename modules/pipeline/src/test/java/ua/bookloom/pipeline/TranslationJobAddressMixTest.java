package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A Ukrainian draft whose one speech writes ти-forms and capitalised Ви-forms together: the soft address-mix finding
 * earns exactly one directed fix that names both forms, and a fix that keeps the mix leaves the draft accepted with
 * the note and spends no second fix.
 */
class TranslationJobAddressMixTest {

    private static final String SOURCE = "“You are tired, Nell. Sit down, you need rest, and I will bring you tea.”";
    private static final String MIXED = "«Ти втомилася, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»";
    private static final String RIGHT = "«Ти втомилася, Нелл. Сядь, тобі треба відпочити, а я принесу тобі чаю.»";

    @TempDir
    private Path tempDir;

    private TestProject newProject() {
        return project(TestBooks.txt(tempDir.resolve("Book.txt"), SOURCE), brief("en", "uk", QualityDial.FAST));
    }

    @Test
    void run_mixedAddressThenFix_repairsTheSegmentWithOneDirectedFixNamingBothForms() {
        final ScriptedChatModel model = replies(MIXED, RIGHT);
        final TestProject project = newProject();

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(userMessage(model.requests().get(1))).contains("Вам").contains("Ти");
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("address-mix");
    }

    @Test
    void run_fixKeepsTheMix_keepsTheDraftAcceptedWithTheNoteAndSpendsNoSecondFix() {
        final ScriptedChatModel model = replies(MIXED, MIXED);
        final TestProject project = newProject();

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.DRAFT);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .contains("address-mix");
    }

    @Test
    void run_speechAddressedOneWay_spendsNoFix() {
        final ScriptedChatModel model = replies(RIGHT);
        final TestProject project = newProject();

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("address-mix");
    }
}
