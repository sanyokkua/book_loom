package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.dial.DialParameters;

/** The one repair is not a replay of the draft: it goes out with the retry seed at the repair's low temperature. */
class QualityLoopReplyRetryTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();

    // IF the repair were sent at the draft's sampling, THEN a model that failed once would tend to fail the same way.
    @Test
    void nextDecision_echoedDraft_repairSendsTheRetrySeedAtTheRepairTemperature() {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("a.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR."));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"items\":[{\"id\":\"1\",\"target\":\"Він відчинив старі двері.\"}]}"));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(RepairMode.ONE, 0, false, ProofreadCoverage.NONE),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());

        final Result<ChunkDecider> started = QualityLoopFixtures.loop()
                .start(
                        List.of(outcome),
                        settings,
                        QualityLoopFixtures.PASSTHROUGH_GATE,
                        (kind, segmentId, request) -> model.chat(request));
        final SegmentOutcome decision = Objects.requireNonNull(
                Objects.requireNonNull(started.data()).nextDecision().data());

        assertThat(decision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        final List<ChatRequest> requests = model.requests();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.seed()).isEqualTo(8191);
            assertThat(request.temperature()).isEqualTo(0.1);
        });
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
