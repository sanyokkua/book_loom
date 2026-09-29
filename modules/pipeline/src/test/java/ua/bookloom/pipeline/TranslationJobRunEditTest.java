package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** What a person saves while a run is paused is still there when the run goes on and stores its next decisions. */
class TranslationJobRunEditTest {

    private static final String EDIT = "Він рвучко відчинив двері.";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // A commit that re-sent the first decision with the later ones would put the machine's record over the edit.
    @Test
    void run_editDuringPause_survivesTheNextCommit() {
        final TestProject project = project(book(), brief("en", "uk"));
        final TranslationJobImpl translation =
                job(project, replies("ВІН ВІДЧИНИВ ДВЕРІ.", "ВОНА ПІШЛА ГЕТЬ.", "ДВЕРІ ЗАЧИНИЛИСЯ."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.subscribe(event -> pauseAfterFirst(translation, event));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        project.stores()
                .segments()
                .update(
                        project.id(),
                        "Book.md:0",
                        record -> record.withUserTarget(EDIT, EDIT).withStatus(SegmentStatus.REVISED));
        translation.resume();
        report(await(run));

        final SegmentRecord edited = stored(project, "Book.md:0");
        assertThat(edited.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(edited.userTarget()).isEqualTo(EDIT);
        assertThat(stored(project, "Book.md:1").status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(stored(project, "Book.md:2").status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    private static void pauseAfterFirst(final TranslationJobImpl translation, final JobEvent event) {
        if (event instanceof SegmentDecided decided && "Book.md:0".equals(decided.segmentId())) {
            translation.pause();
        }
    }

    private Path book() {
        return TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the door.\n\nShe left.\n\nThe door closed.");
    }
}
