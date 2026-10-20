package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** The progress an export announces keeps its counts coherent, and a service without progress still serves a job. */
class ExportProgressTest {

    // IF done could exceed total, THEN a bar would be drawn past full.
    @ParameterizedTest
    @CsvSource({"-1,3", "4,3"})
    void new_countsOutsideTheRange_isRejected(final int done, final int total) {
        assertThatThrownBy(() -> new ExportProgress(ExportProgress.Step.WRITING, done, total))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void new_doneEqualToTotal_isAccepted() {
        assertThat(new ExportProgress(ExportProgress.Step.WRITING, 3, 3).done()).isEqualTo(3);
    }

    @Test
    void new_threeArgs_hasNoItem() {
        final ExportProgress progress = new ExportProgress(ExportProgress.Step.WRITING, 1, 2);

        assertThat(progress.itemRef()).isNull();
        assertThat(progress.before()).isNull();
        assertThat(progress.after()).isNull();
        assertThat(progress.verdict()).isNull();
    }

    @Test
    void new_withItem_keepsReferenceTextsAndVerdict() {
        final ExportProgress progress = new ExportProgress(
                ExportProgress.Step.RETRY_DOUBTED, 1, 4, "Book.md:2", "Old text.", "New text.", "kept");

        assertThat(progress.itemRef()).isEqualTo("Book.md:2");
        assertThat(progress.before()).isEqualTo("Old text.");
        assertThat(progress.after()).isEqualTo("New text.");
        assertThat(progress.verdict()).isEqualTo("kept");
    }

    // IF the default overload dropped the request, THEN a service that does not report progress could not export.
    @Test
    void newExport_serviceWithoutProgress_delegatesToTheTwoArgumentFactory() {
        final List<ExportRequest> seen = new ArrayList<>();
        final ExportService plain = (request, model) -> {
            seen.add(request);
            return Result.err(AppError.of(ErrorCode.internal, "none", "none"));
        };
        final ExportRequest request =
                new ExportRequest("p", java.nio.file.Path.of("out.epub"), false, java.util.Set.of(), false);

        plain.newExport(request, null, ExportProgressListener.NONE);

        assertThat(seen).containsExactly(request);
    }
}
