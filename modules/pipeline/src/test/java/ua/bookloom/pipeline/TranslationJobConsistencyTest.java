package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.JUDGE;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
import static ua.bookloom.pipeline.ChunkRunFixtures.S3;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.judged;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A run hides each chunk's locked names and kept foreign runs behind tokens, shows each draft its context package,
 * stores with every decided record the snapshot of what its draft saw, and reuses the translation memory where a
 * segment's context matches.
 */
class TranslationJobConsistencyTest {

    private static final String CHAPTER_ONE = "OEBPS/ch0.xhtml:0";
    private static final String CHAPTER_TWO = "OEBPS/ch1.xhtml:0";
    private static final String PAUSED = "Він зупинився.";
    private static final String SMILED = "Вона всміхнулася.";
    private static final String LATE = "Було пізно.";
    private static final String LEFT = "Вони пішли.";

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

    @Test
    void run_repeatedPassageInSameContext_isReused() {
        final TestProject project = project(twoChapters(tempDir, "Yes."), epubBriefOn(QualityDial.BALANCED));
        final ScriptedChatModel model = replies(PAUSED, "Так.", SMILED, LATE, PAUSED, SMILED, LEFT)
                .answerTo(JUDGE, judged())
                .answerTo(JUDGE, judged())
                .answerTo(JUDGE, judged());
        final TranslationJobImpl translation = job(project, model);
        final List<MemoryUpdated> memory = memoryEvents(translation);

        report(translation.run());

        final List<String> drafts = messagesOf(model, DRAFT);
        assertThat(drafts)
                .hasSize(7)
                .filteredOn(message -> message.contains(shown("Yes.")))
                .hasSize(1);
        final SegmentRecord reused = stored(project, "OEBPS/ch1.xhtml:2");
        assertThat(reused)
                .extracting(
                        SegmentRecord::status,
                        SegmentRecord::machineTarget,
                        SegmentRecord::path,
                        SegmentRecord::confidence)
                .containsExactly(SegmentStatus.ACCEPTED, "Так.", SegmentPath.TM_REUSE, 1.0);
        assertThat(reused.context()).isNotNull();
        assertThat(memory).containsExactlyElementsOf(reuseBetweenSummaries());
        assertThat(drafts.get(4)).contains("He paused. → " + PAUSED);
        assertThat(drafts.get(5)).contains("She smiled. → " + SMILED);
        assertThat(messagesOf(model, JUDGE).get(1))
                .contains("[s1]\nSource: It was late.", "[s2]\nSource: He paused.", "[s3]\nSource: She smiled.")
                .doesNotContain("Yes.", "[s4]");
    }

    @Test
    void run_reuseFailingProtectedSpan_isDrafted() {
        final TestProject project = project(twoChapters(tempDir, "Hale nodded."), epubBriefOn(QualityDial.FAST));
        final ScriptedChatModel model =
                replies(PAUSED, "Хейл кивнув.", SMILED, LATE, PAUSED, "⟦g0⟧ кивнув.", SMILED, LEFT);
        final TranslationJobImpl translation = job(project, model);

        runPausedAfterFirstChapter(
                translation, () -> addTerm(project, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));

        assertThat(messagesOf(model, DRAFT)).hasSize(8).anyMatch(message -> message.contains(shown("⟦g0⟧ nodded.")));
        assertThat(stored(project, "OEBPS/ch1.xhtml:2"))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::path)
                .containsExactly("Гейл кивнув.", SegmentPath.DRAFT);
    }

    @Test
    void run_reviewEdit_leavesMemoryUnchanged() {
        final TestProject project = project(twoChapters(tempDir, "Yes."), epubBriefOn(QualityDial.FAST));
        final ScriptedChatModel model = replies(PAUSED, "Так.", SMILED, LATE, PAUSED, SMILED, LEFT);
        final TranslationJobImpl translation = job(project, model);

        runPausedAfterFirstChapter(
                translation,
                () -> project.stores()
                        .segments()
                        .update(
                                project.id(),
                                "OEBPS/ch0.xhtml:1",
                                record ->
                                        record.withUserTarget("Атож.", "Атож.").withStatus(SegmentStatus.REVISED)));

        assertThat(messagesOf(model, DRAFT)).hasSize(7);
        assertThat(stored(project, "OEBPS/ch1.xhtml:2"))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::path)
                .containsExactly("Так.", SegmentPath.TM_REUSE);
    }

    @Test
    void run_flaggedSegment_writesNoMemoryEntry() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(target(T0))
                .answer(cutOffReply())
                .answer(target(T2))
                .answer(target(T3));
        final TestProject project = project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));

        report(job(project, model).run());

        assertThat(stored(project, "Book.md:1").status()).isEqualTo(SegmentStatus.FLAGGED);
        final List<TmEntry> entries = Objects.requireNonNull(
                project.stores()
                        .tm()
                        .candidates(project.id(), 0, Integer.MAX_VALUE)
                        .data(),
                "entries");
        assertThat(entries)
                .extracting(TmEntry::sourceInner, TmEntry::targetInner)
                .containsExactlyInAnyOrder(tuple(S0, T0), tuple(S2, T2), tuple(S3, T3));
    }

    /** Two chapters around {@code repeated}: the second repeats the first's three lines between two new ones. */
    private static Path twoChapters(final Path directory, final String repeated) {
        return TestBooks.epub(
                directory.resolve("Book.epub"),
                List.of(
                        List.of("He paused.", repeated, "She smiled."),
                        List.of("It was late.", "He paused.", repeated, "She smiled.", "They left.")),
                "en");
    }

    /** Runs with a pause after each chapter, doing {@code duringPause} in the first. */
    private static void runPausedAfterFirstChapter(final TranslationJobImpl translation, final Runnable duringPause) {
        final LinkedBlockingQueue<Paused> paused = pauses(translation);
        translation.pauseAt(Set.of(PausePoint.AFTER_SECTION));
        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(paused);
        duringPause.run();
        translation.resume();
        awaitPaused(paused);
        translation.resume();
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
    }

    /** The one reuse, announced between the summaries refreshed at the end of each chapter. */
    private static List<MemoryUpdated> reuseBetweenSummaries() {
        return List.of(
                new MemoryUpdated(MemoryKind.SUMMARY, "1"),
                new MemoryUpdated(MemoryKind.TM, "ch2 · p03"),
                new MemoryUpdated(MemoryKind.SUMMARY, "2"));
    }

    private static List<MemoryUpdated> memoryEvents(final TranslationJobImpl translation) {
        final List<MemoryUpdated> events = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> {
            if (event instanceof MemoryUpdated updated) {
                events.add(updated);
            }
        });
        return events;
    }

    private static List<String> messagesOf(final ScriptedChatModel model, final String format) {
        return model.requests().stream()
                .filter(request -> format.equals(Objects.requireNonNull(request.responseFormat(), "format")
                        .name()))
                .map(ChunkRunFixtures::userMessage)
                .toList();
    }

    /** The EPUB job brief — metadata and navigation kept as source — on the given dial. */
    private static BookBrief epubBriefOn(final QualityDial dial) {
        final BookBrief fast = TranslationJobTestSupport.epubBrief();
        return new BookBrief(
                fast.sourceLanguage(),
                fast.targetLanguage(),
                fast.genre(),
                fast.register(),
                fast.voiceEra(),
                fast.audience(),
                fast.names(),
                fast.foreignPassages(),
                fast.footnotes(),
                fast.units(),
                fast.balance(),
                fast.alsoTranslate(),
                dial);
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
