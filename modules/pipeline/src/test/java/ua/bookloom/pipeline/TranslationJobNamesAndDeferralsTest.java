package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.pauses;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.epubBrief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A run proposes the names a unit introduces into the unit's last commit and announces each glossary update, and
 * stores with each chunk the deferrals a character of unknown gender leaves.
 */
class TranslationJobNamesAndDeferralsTest {

    private static final String LAST_OF_CHAPTER_TWO = "OEBPS/ch1.xhtml:1";
    private static final String[] MOREAU_REPLIES = {
        "Вони зустріли Моро опівдні.",
        "Вона знову побачила Моро.",
        "Він сказав Моро новину.",
        "Вони залишили Моро самого."
    };
    private static final String HALE_BOOK =
            "We met Hale.\n\nThen Hale left.\n\nLater Hale ran.\n\nSo Hale won.\n\nAnd Hale slept.";
    private static final String[] HALE_REPLIES = {
        "Ми зустріли Гейла.", "Потім Гейл пішов.", "Згодом Гейл побіг.", "Тож Гейл переміг.", "І Гейл спав."
    };

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_unitEndScan_addsRecurringName() {
        final TestProject project = project(moreauChapters(), epubBrief());
        final GlossaryEntry hale = add(project, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        final TranslationJobImpl translation = job(project, replies(MOREAU_REPLIES));
        final List<String> events = described(translation);
        final AtomicReference<List<GlossaryEntry>> afterChapterOne = new AtomicReference<>();

        runPausedAfterFirstChapter(translation, () -> afterChapterOne.set(glossary(project)));

        assertThat(afterChapterOne.get()).containsExactly(hale);
        assertThat(glossary(project))
                .containsExactlyInAnyOrder(
                        hale,
                        new GlossaryEntry(
                                GlossaryIds.of(project.id(), "Moreau"),
                                project.id(),
                                "Moreau",
                                null,
                                TermType.OTHER,
                                Gender.UNKNOWN,
                                false));
        assertThat(events).filteredOn(event -> event.startsWith("GLOSSARY")).containsExactly("GLOSSARY +1");
        assertThat(events).containsSubsequence("decided " + LAST_OF_CHAPTER_TWO, "GLOSSARY +1");
    }

    @Test
    void run_unitEndScan_skipsRemovedTerm() {
        final TestProject project = project(moreauChapters(), epubBrief());
        final GlossaryEntry hale = add(project, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        final GlossaryEntry moreau = add(project, "Moreau", "Моро", TermType.CHARACTER, Gender.MALE, false);
        Objects.requireNonNull(
                project.stores().glossary().remove(project.id(), moreau.id()).data(), "removed");
        final TranslationJobImpl translation = job(project, replies(MOREAU_REPLIES));
        final List<String> events = described(translation);

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(glossary(project)).containsExactly(hale);
        assertThat(events).noneMatch(event -> event.startsWith("GLOSSARY"));
    }

    @Test
    void run_prepScanAddingNames_announcesThem() {
        final TestProject project =
                project(TestBooks.markdown(tempDir.resolve("Book.md"), HALE_BOOK), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies(HALE_REPLIES));
        final List<String> events = described(translation);

        report(translation.run());

        assertThat(events.subList(0, 3)).containsExactly("stage PREP", "GLOSSARY +1", "stage TRANSLATE");
        assertThat(events).filteredOn(event -> event.startsWith("GLOSSARY")).containsExactly("GLOSSARY +1");
    }

    @Test
    void run_prepScanFindingNoName_announcesNothing() {
        final TestProject project = project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies(T0, T1, T2, T3));
        final List<String> events = described(translation);

        report(translation.run());

        assertThat(events).noneMatch(event -> event.startsWith("GLOSSARY"));
    }

    @Test
    void run_unknownGender_recordsDeferral() {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"), "Sam opened the door.\n\nThe wind rose over the dark sea."),
                brief("en", "uk"));
        add(project, "Sam", "Сем", TermType.CHARACTER, Gender.UNKNOWN, false);

        report(job(project, replies("Сем відчинив двері.", T3)).run());

        assertThat(openDeferrals(project))
                .containsExactly(new Deferral(
                        project.id() + ":Book.md:0:GENDER_UNKNOWN:Sam",
                        project.id(),
                        "Book.md:0",
                        DeferralReason.GENDER_UNKNOWN,
                        "Sam",
                        null,
                        null,
                        null));
    }

    /** Two chapters naming Moreau twice each, never at a sentence start: only the whole book reaches the scan's three. */
    private Path moreauChapters() {
        return TestBooks.epub(
                tempDir.resolve("Book.epub"),
                List.of(
                        List.of("They met Moreau at noon.", "She saw Moreau again."),
                        List.of("He told Moreau the news.", "They left Moreau alone.")),
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

    /** Each stage start, decision and memory update the job announces, as one line each, in order. */
    private static List<String> described(final TranslationJobImpl translation) {
        final List<String> events = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> describe(event).ifPresent(events::add));
        return events;
    }

    private static Optional<String> describe(final JobEvent event) {
        return switch (event) {
            case StageStarted started -> Optional.of("stage " + started.stage());
            case SegmentDecided decided -> Optional.of("decided " + decided.segmentId());
            case MemoryUpdated updated -> Optional.of(updated.kind() + " " + updated.label());
            default -> Optional.empty();
        };
    }

    private static List<GlossaryEntry> glossary(final TestProject project) {
        return Objects.requireNonNull(
                project.stores().glossary().all(project.id()).data(), "glossary");
    }

    private static List<Deferral> openDeferrals(final TestProject project) {
        return Objects.requireNonNull(project.deferrals().open(project.id()).data(), "open deferrals");
    }

    private static GlossaryEntry add(
            final TestProject project,
            final String term,
            final String rendering,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        final GlossaryEntry entry = new GlossaryEntry(
                GlossaryIds.of(project.id(), term), project.id(), term, rendering, type, gender, locked);
        return Objects.requireNonNull(project.stores().glossary().add(entry).data(), "added " + term);
    }
}
