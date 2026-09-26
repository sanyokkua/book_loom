package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.documents;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;

/** The job tells its listeners when a request is really sent to the model, so a screen can say the model is slow. */
class TranslationJobModelCallEventTest {

    @TempDir
    private Path tempDir;

    // Emitting per segment instead of per call, or after the decision, would hide a slow first call.
    @Test
    void run_twoSegments_emitsOneModelCallStartedBeforeEachDecision() {
        final TranslationJobImpl translation = job(
                documents(),
                TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo."),
                tempDir.resolve("Book.uk.md"),
                replies("ONE.", "TWO."));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        translation.run();

        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "StageStarted",
                        "ModelCallStarted",
                        "SegmentDecided",
                        "ModelCallStarted",
                        "SegmentDecided",
                        "StageStarted",
                        "Finished");
        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .extracting(event -> ((ModelCallStarted) event).segmentId())
                .containsExactly("Book.md:0", "Book.md:1");
    }
}
