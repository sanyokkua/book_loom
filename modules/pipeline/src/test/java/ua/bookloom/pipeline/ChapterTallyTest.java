package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.SummaryBooks.CHAPTER_SEGMENTS;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ChapterTally;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.project.ChapterStandings;
import ua.bookloom.pipeline.project.ProjectServiceImpl;

/**
 * How many finished chapters turning chapter summaries on would summarise before the next passage
 * ({@code specs/book-brief/spec.md} "Say how many finished chapters will be summarised when the switch is turned on"):
 * by the same rule the next Continue writes them, so a finished chapter with no summary, or one written from other
 * source text, counts, and the book's last chapter, which no later window reads, never does.
 */
class ChapterTallyTest {

    @TempDir
    private Path tempDir;

    private final Injector injector = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule());

    private ProjectServiceImpl service(final TestProject project) {
        return ProjectServices.service(
                injector,
                injector.getInstance(DocumentPort.class),
                project.stores().projects(),
                project.stores().segments(),
                project.stores().openProjects());
    }

    private static void decideChapters(final TestProject project, final int chapters) {
        final List<SegmentRecord> records = Objects.requireNonNull(
                project.stores().segments().all(project.id()).data(), "records");
        records.stream()
                .limit((long) chapters * CHAPTER_SEGMENTS)
                .forEach(record -> project.stores()
                        .segments()
                        .update(project.id(), record.segmentId(), stored -> stored.withStatus(SegmentStatus.ACCEPTED)));
    }

    private static List<Segment> chapterSegments(final TestProject project, final int number) {
        return Objects.requireNonNull(project.stores().openProjects().get(project.id()), "document").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .skip((long) (number - 1) * CHAPTER_SEGMENTS)
                .limit(CHAPTER_SEGMENTS)
                .toList();
    }

    private static void storeSummary(final TestProject project, final int number, final String sourceHash) {
        project.stores()
                .summaries()
                .save(new ChapterSummary(
                        project.id(),
                        "Book.md:" + (number - 1) * CHAPTER_SEGMENTS,
                        "Book.md",
                        (number - 1) * CHAPTER_SEGMENTS,
                        CHAPTER_SEGMENTS,
                        "Підсумок.",
                        sourceHash));
    }

    private ChapterTally tally(final TestProject project) {
        final Result<ChapterTally> tally = service(project).chapterTally(project.id());
        return Objects.requireNonNull(tally.data(), () -> "tally: " + tally.error());
    }

    @Test
    void tally_twelveFinishedNoneStored_twelve() {
        final TestProject project = SummaryBooks.book(injector, tempDir, 13, false);
        decideChapters(project, 12);

        assertThat(tally(project)).isEqualTo(new ChapterTally(13, 12, 12));
    }

    @Test
    void tally_lastChapterFinished_notCounted() {
        final TestProject project = SummaryBooks.book(injector, tempDir, 4, false);
        decideChapters(project, 4);

        assertThat(tally(project)).isEqualTo(new ChapterTally(4, 4, 3));
    }

    // Chapter 1's summary was written from the source it holds now; chapter 2's from other text, so it is written
    // again.
    @Test
    void tally_storedFresh_notCounted() {
        final TestProject project = SummaryBooks.book(injector, tempDir, 4, true);
        decideChapters(project, 3);
        storeSummary(project, 1, ChapterStandings.sourceHash(chapterSegments(project, 1)));
        storeSummary(project, 2, "stale");

        assertThat(tally(project)).isEqualTo(new ChapterTally(4, 3, 2));
    }

    @Test
    void tally_nothingFinished_none() {
        final TestProject project = SummaryBooks.book(injector, tempDir, 4, false);

        assertThat(tally(project)).isEqualTo(new ChapterTally(4, 0, 0));
    }

    @Test
    void tally_unknownProject_isAnError() {
        final TestProject project = SummaryBooks.book(injector, tempDir, 2, false);

        assertThat(service(project).chapterTally("no such project").isErr()).isTrue();
    }
}
