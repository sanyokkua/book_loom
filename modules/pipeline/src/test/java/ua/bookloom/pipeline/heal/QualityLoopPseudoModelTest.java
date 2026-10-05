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
 * 0.85 — the reviewer call reads only the short line, because the echo is refused by the checks, and the pseudo
 * reviewer never asks for an edit
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

        // One reviewer call (for the short line only) plus one directed fix for the echo; no second fix because the
        // pseudo model's fix returns the echo unchanged.
        assertThat(requests).hasSize(2);
        assertThat(responseFormatNames(requests)).containsExactly("reviewer", "directed-fix");

        assertThat(flagged.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(flagged.machineTarget()).isEqualTo("HE OPENED THE *OLD* DOOR.");
        assertThat(flagged.maskedMachineTarget()).isEqualTo("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.");
        assertThat(flagged.judgeScore()).isNull();
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
