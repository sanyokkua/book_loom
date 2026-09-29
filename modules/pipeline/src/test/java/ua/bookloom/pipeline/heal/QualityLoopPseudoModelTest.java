package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link QualityLoop} driven by the real, deterministic {@link PseudoChatModel} on Balanced, over the three review
 * modes: an echo the pseudo model can never fix (D8's reference fixture) is FLAGGED after its repair budget, and a
 * short line the pseudo model translates correctly by chance ("Yes, sir." → "YES, SIR.") is ACCEPTED at confidence
 * 0.85 — both sharing the chunk's one judge call, which the pseudo model always answers perfectly
 * ({@code design.md} D8 "Reference fixtures").
 */
class QualityLoopPseudoModelTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();
    private final QualityLoop loop = QualityLoopFixtures.loop();
    private final List<ChatRequest> requests = new ArrayList<>();
    private final ModelCalls calls = recordingPseudoCalls(requests);

    @ParameterizedTest
    @EnumSource(ReviewMode.class)
    void nextDecision_pseudoModelOnBalanced_flagsTheEchoAndAcceptsTheShortLine(final ReviewMode mode) {
        final Segment echoSegment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo.md"), "He opened the *old* door.");
        final DraftOutcome.Drafted echoOutcome =
                QualityLoopFixtures.drafted(echoSegment, documents, "HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.");
        assertThat(echoOutcome.restoredTarget()).isEqualTo("HE OPENED THE *OLD* DOOR.");
        final Segment shortSegment = QualityLoopFixtures.markdownSegment(tempDir.resolve("short.md"), "Yes, sir.");
        final DraftOutcome.Drafted shortOutcome = QualityLoopFixtures.drafted(shortSegment, documents, "YES, SIR.");

        final LoopSettings settings = QualityLoopFixtures.settings(mode, QualityDial.BALANCED);
        final GateFunction gate = GateFunction.of(documents, BookFormat.MARKDOWN);
        final ChunkDecider decider =
                Objects.requireNonNull(loop.start(List.of(echoOutcome, shortOutcome), settings, gate, calls)
                        .data());

        final SegmentOutcome flagged =
                Objects.requireNonNull(decider.nextDecision().data());
        final SegmentOutcome accepted =
                Objects.requireNonNull(decider.nextDecision().data());

        // One judge call (shared by both segments) plus exactly two directed fixes for the echo; no re-judge
        // because its confidence never reaches τ, and the pseudo model's echo never stops failing outright.
        assertThat(requests).hasSize(3);
        assertThat(responseFormatNames(requests)).containsExactly("judge", "directed-fix", "directed-fix");

        assertThat(flagged.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(flagged.machineTarget()).isEqualTo("HE OPENED THE *OLD* DOOR.");
        assertThat(flagged.maskedMachineTarget()).isEqualTo("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.");
        // B2: neither repair round was ever re-judged, so the chunk verdict the pseudo model always answers
        // perfectly is still the verdict that last decided this segment.
        assertThat(flagged.judgeScore()).isEqualTo(1.0);
        assertThat(accepted.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(accepted.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(accepted.confidence()).isEqualTo(0.85);
    }

    private static List<String> responseFormatNames(final List<ChatRequest> requests) {
        return requests.stream()
                .map(request -> Objects.requireNonNull(request.responseFormat()).name())
                .toList();
    }

    private static ModelCalls recordingPseudoCalls(final List<ChatRequest> requests) {
        final PseudoChatModel pseudo = new PseudoChatModel(new ObjectMapper());
        return (kind, segmentId, request) -> {
            requests.add(request);
            return pseudo.chat(request);
        };
    }
}
