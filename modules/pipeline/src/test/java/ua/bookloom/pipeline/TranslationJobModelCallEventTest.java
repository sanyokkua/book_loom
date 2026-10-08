package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.targetReply;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;

/** The job tells its listeners when a request is really sent to the model, so a screen can say the model is slow. */
class TranslationJobModelCallEventTest {

    /** What one segment drafted and accepted on its first call announces, in order. */
    private static final List<String> ONE_SEGMENT = List.of(
            "SegmentStarted",
            "ContextAssembled",
            "ModelCallStarted",
            "CallSnapshotUpdated",
            "ModelCallFinished",
            "CallSnapshotUpdated",
            "SegmentDrafted",
            "SegmentDecided",
            "CallSnapshotUpdated");

    @TempDir
    private Path tempDir;

    // Emitting per segment instead of per call, or after the decision, would hide a slow first call.
    @Test
    void run_twoSegments_emitsOneModelCallStartedBeforeEachDecision() {
        final TranslationJobImpl translation =
                job(TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo."), replies("ONE.", "TWO."));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        translation.run();

        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactlyElementsOf(Stream.of(
                                List.of("StageStarted", "StageStarted"), ONE_SEGMENT, ONE_SEGMENT, List.of("Finished"))
                        .flatMap(List::stream)
                        .toList());
        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .extracting(event -> ((ModelCallStarted) event).segmentId())
                .containsExactly("Book.md:0", "Book.md:1");
    }

    // The old announcement named DRAFT for a repair too, so a screen could not say what the model was doing.
    @Test
    void run_structuralRepair_announcesDraftThenStructuralRepairForTheSameSegment() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"segments\":", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(targetReply("ONE."), FinishReason.STOP)));
        final TranslationJobImpl translation = job(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        translation.run();

        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .map(event -> ((ModelCallStarted) event).segmentId() + " " + ((ModelCallStarted) event).kind())
                .containsExactly("Book.md:0 DRAFT", "Book.md:0 STRUCTURAL_REPAIR");
    }
}
