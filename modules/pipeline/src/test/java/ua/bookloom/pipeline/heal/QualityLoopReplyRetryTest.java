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
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.dial.DialParameters;

/** A repair round whose reply was refused is not repeated byte for byte: the cause and another seed go with the retry. */
class QualityLoopReplyRetryTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();

    // IF the second request were identical to the first, THEN a model that failed once would fail the same way again.
    @Test
    void nextDecision_unpairableControlCodes_retryNamesTheCauseAndSendsAnotherSeed() {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("a.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"target\":\"\\u001eВін відчинив старі двері.\"}"))
                .answer(readable("{\"target\":\"Він відчинив старі двері.\"}"));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(1, 2, 0, false, false, 4),
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
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().seed()).isNull();
        assertThat(requests.get(1).seed()).isNotNull();
        assertThat(requests.get(1).messages()).isNotEqualTo(requests.getFirst().messages());
        assertThat(requests.get(1).messages().getLast().content()).contains("held control characters");
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
