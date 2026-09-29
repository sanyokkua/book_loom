package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.pauses;
import static ua.bookloom.pipeline.ChunkRunFixtures.shown;
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.cutOffReply;
import static ua.bookloom.pipeline.TranslationJobTestSupport.epubBrief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A run hides each chunk's locked names and kept foreign runs behind tokens, shows each draft its context package, and
 * stores with every decided record the snapshot of what its draft saw.
 */
class TranslationJobConsistencyTest {

    private static final String CHAPTER_ONE = "OEBPS/ch0.xhtml:0";
    private static final String CHAPTER_TWO = "OEBPS/ch1.xhtml:0";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_contextPackage_injectsOnlyTermsInChunk() {
        final TestProject project =
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "Hale opened the door."), brief("en", "uk"));
        addTerm(project, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
        addTerm(project, "Baker Street", "Бейкер-стріт", TermType.PLACE, Gender.NEUTER, false);
        final ScriptedChatModel model = replies("Гейл відчинив двері.");

        report(job(project, model).run());

        assertThat(userMessage(model.requests().getFirst()))
                .contains("Hale", "Гейл", "character", "male")
                .doesNotContain("Baker Street");
        assertThat(snapshotOf(project, "Book.txt:0").glossary())
                .containsExactly(new SnapshotTerm("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false));
    }

    @Test
    void run_lockedTerm_maskedAndRestored() {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Hale opened the *old* door."), brief("en", "uk"));
        addTerm(project, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        final ScriptedChatModel model = replies("⟦g2⟧ відчинив ⟦g0⟧старі⟦g1⟧ двері.");

        report(job(project, model).run());

        assertThat(userMessage(model.requests().getFirst()))
                .contains(shown("⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door."))
                .contains("⟦g2⟧ → Гейл, character, male");
        assertThat(stored(project, "Book.md:0"))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::maskedMachineTarget)
                .containsExactly("Гейл відчинив *старі* двері.", "Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері.");
    }

    @Test
    void run_keptForeignRun_sentAsOnePlaceholder() {
        final Path book = TestBooks.epub(
                tempDir.resolve("Book.epub"),
                List.of(List.of("She whispered <i xml:lang=\"fr\">au revoir</i> and left.")),
                "en");
        final TestProject project = project(book, epubBrief());
        final ScriptedChatModel model = replies("Вона прошепотіла ⟦g2⟧ і пішла.");

        report(job(project, model).run());

        assertThat(userMessage(model.requests().getFirst())).contains(shown("She whispered ⟦g2⟧ and left."));
        assertThat(stored(project, CHAPTER_ONE).machineTarget())
                .isEqualTo("Вона прошепотіла <i xml:lang=\"fr\">au revoir</i> і пішла.");
    }

    @Test
    void run_lockDuringAfterSectionPause_masksOnlyFromTheNextChunk() {
        final Path book = TestBooks.epub(
                tempDir.resolve("Book.epub"),
                List.of(List.of("Hale opened the door."), List.of("Hale closed the door.")),
                "en");
        final TestProject project = project(book, epubBrief());
        final ScriptedChatModel model = replies("Гейл відчинив двері.", "⟦g0⟧ зачинив двері.");
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> paused = pauses(translation);
        translation.pauseAt(Set.of(PausePoint.AFTER_SECTION));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(paused);
        addTerm(project, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        translation.resume();
        awaitPaused(paused);
        translation.resume();
        final JobReport ended = report(await(run));

        assertThat(userMessage(model.requests().getFirst())).contains(shown("Hale opened the door."));
        assertThat(userMessage(model.requests().get(1))).contains(shown("⟦g0⟧ closed the door."));
        assertThat(ended.end()).isEqualTo(JobState.COMPLETED);
        assertThat(stored(project, CHAPTER_ONE).machineTarget()).isEqualTo("Гейл відчинив двері.");
        assertThat(stored(project, CHAPTER_TWO).machineTarget()).isEqualTo("Гейл зачинив двері.");
    }

    @Test
    void run_everyDecidedRecord_carriesItsSnapshot() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(target(T0))
                .answer(cutOffReply())
                .answer(target(T2))
                .answer(target(T3));
        final TestProject project = project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));

        report(job(project, model).run());

        assertThat(List.of("Book.md:0", "Book.md:1", "Book.md:2", "Book.md:3"))
                .extracting(id -> stored(project, id).status())
                .containsExactly(
                        SegmentStatus.ACCEPTED, SegmentStatus.FLAGGED, SegmentStatus.ACCEPTED, SegmentStatus.ACCEPTED);
        assertThat(List.of("Book.md:0", "Book.md:1", "Book.md:2", "Book.md:3"))
                .extracting(id -> snapshotOf(project, id).precedingTargets())
                .containsExactly(List.of(), List.of(T0), List.of(T0), List.of(T2));
        assertThat(snapshotOf(project, "Book.md:3").styleSheet()).contains("faithful literary prose");
    }

    private static ContextSnapshot snapshotOf(final TestProject project, final String segmentId) {
        return Objects.requireNonNull(stored(project, segmentId).context(), "context snapshot of " + segmentId);
    }

    private static void addTerm(
            final TestProject project,
            final String term,
            final String rendering,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        final GlossaryEntry entry = new GlossaryEntry(
                GlossaryIds.of(project.id(), term), project.id(), term, rendering, type, gender, locked);
        Objects.requireNonNull(project.stores().glossary().add(entry).data(), "added " + term);
    }
}
