package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.TestDocuments;

/**
 * {@link GateFunction#of}: the real placeholder gate answers which gate failed and what it found, or the masked form
 * and the restored text of a target it accepted.
 */
class GateFunctionTest {

    private static final String MARKDOWN_SOURCE = "He opened the *old* door.";

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();

    @Test
    void restore_closingTokenDropped_answersGateFailedWithAHighMarkupFindingRaisedByPlaceholder() {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("a.md"), MARKDOWN_SOURCE);

        final GateResult result =
                GateFunction.of(documents, BookFormat.MARKDOWN).restore(segment, "Він відчинив ⟦g0⟧старі двері.");

        assertThat(result).isInstanceOfSatisfying(GateResult.GateFailed.class, failed -> {
            assertThat(failed.finding().kind()).isEqualTo("markup");
            assertThat(failed.finding().severity()).isEqualTo(Severity.HIGH);
            assertThat(failed.finding().raisedBy()).isEqualTo("placeholder");
            assertThat(failed.finding().note()).isEqualTo(failed.error().message());
            assertThat(failed.error().code()).isEqualTo(ErrorCode.validation);
            assertThat(failed.error().details()).contains("⟦g1⟧");
        });
    }

    @Test
    void restore_pairKeptAroundWords_answersRestoredWithTheSameMaskedFormAndTheRestoredText() {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("b.md"), MARKDOWN_SOURCE);
        final String masked = "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.";

        final GateResult result =
                GateFunction.of(documents, BookFormat.MARKDOWN).restore(segment, masked);

        assertThat(result)
                .isEqualTo(new GateResult.Restored("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.", "Він відчинив *старі* двері."));
    }

    @Test
    void restore_documentPortFailsWithAnyOtherCode_answersStepErrorCarryingThatError() {
        final AppError failure = AppError.of(ErrorCode.internal, "Unmask exploded", "boom");
        final DocumentPort failing = new DocumentPort() {
            @Override
            public Result<Document> open(final Path source) {
                return Result.err(failure);
            }

            @Override
            public Result<Path> write(final Document document, final Path destination, final String targetLanguage) {
                return Result.err(failure);
            }

            @Override
            public Result<Boolean> close(final Document document) {
                return Result.err(failure);
            }

            @Override
            public Result<String> unmask(
                    final BookFormat format, final Segment segment, final String translatedMasked) {
                return Result.err(failure);
            }
        };
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("c.md"), MARKDOWN_SOURCE);

        final GateResult result = GateFunction.of(failing, BookFormat.MARKDOWN).restore(segment, "x");

        assertThat(result).isEqualTo(new GateResult.StepError(failure));
    }
}
