package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ExportProgress;

/** The seam's two additions are inert by default, so a seam that shows nothing needs no change. */
class ModelCallsStoppingTest {

    // IF the default said "stopping", THEN every ordinary run would skip its own calls.
    @Test
    void isStoppingEarly_defaultSeam_isFalse() {
        final ModelCalls plain = (kind, segmentId, request) -> Result.ok(new ChatResponse("x", FinishReason.STOP));

        assertThat(plain.isStoppingEarly()).isFalse();
    }

    // IF the four-argument reread were dropped, THEN a stage that cannot name neighbours would lose its announcement.
    @Test
    void reread_fourArguments_delegatesToTheFiveArgumentFormWithNoNeighbours() {
        final List<ExportProgress.@Nullable Neighbours> seen = new ArrayList<>();
        final ModelCalls recording = new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final ua.bookloom.api.pipeline.CallKind kind,
                    @Nullable final String segmentId,
                    final ua.bookloom.api.llm.ChatRequest request) {
                return Result.ok(new ChatResponse("x", FinishReason.STOP));
            }

            @Override
            public void reread(
                    final String segmentId,
                    final String before,
                    @Nullable final String after,
                    final String verdict,
                    final ExportProgress.@Nullable Neighbours neighbours) {
                seen.add(neighbours);
            }
        };

        recording.reread("Book.md:1", "Було.", "Стало.", "fixed");

        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst()).isNull();
    }
}
