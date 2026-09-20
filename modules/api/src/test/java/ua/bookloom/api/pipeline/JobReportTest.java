package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;

/**
 * {@code JobReport}'s terminal-state and conditional-component invariants.
 */
class JobReportTest {

    private static final AppError FAILURE =
            AppError.of(ErrorCode.unreachable, "Unreachable", "The model is unreachable.");

    @ParameterizedTest
    @EnumSource(
            value = JobState.class,
            names = {"NEW", "RUNNING", "PAUSED"})
    void constructor_nonTerminalEndState_isRejected(final JobState end) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobReport(BookFormat.MARKDOWN, end, 0, 0, 0, List.of(), null, null));
    }

    @Test
    void constructor_failedWithoutError_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobReport(BookFormat.MARKDOWN, JobState.FAILED, 1, 0, 0, List.of(), null, null));
    }

    @Test
    void constructor_cancelledWithError_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new JobReport(BookFormat.MARKDOWN, JobState.CANCELLED, 1, 0, 0, List.of(), null, FAILURE));
    }

    @Test
    void constructor_cancelledWithWrittenPath_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobReport(
                        BookFormat.MARKDOWN, JobState.CANCELLED, 1, 0, 0, List.of(), Path.of("Book.uk.md"), null));
    }

    @Test
    void constructor_failedWithWrittenPath_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobReport(
                        BookFormat.MARKDOWN, JobState.FAILED, 1, 0, 0, List.of(), Path.of("Book.uk.md"), FAILURE));
    }

    @Test
    void flaggedSegments_constructorCopiesCallerList() {
        final FlaggedSegment flagged = new FlaggedSegment("book:0", ErrorCode.validation);
        final List<FlaggedSegment> flaggedSegments = new ArrayList<>(List.of(flagged));

        final JobReport report = new JobReport(
                BookFormat.MARKDOWN, JobState.COMPLETED, 1, 0, 1, flaggedSegments, Path.of("Book.uk.md"), null);
        flaggedSegments.clear();

        assertThat(report.flaggedSegments()).containsExactly(flagged);
    }

    @Test
    void constructor_completedWithWrittenPath_isValid() {
        final FlaggedSegment flagged = new FlaggedSegment("book:0", ErrorCode.validation);

        final JobReport report = new JobReport(
                BookFormat.MARKDOWN, JobState.COMPLETED, 2, 1, 1, List.of(flagged), Path.of("Book.uk.md"), null);

        assertThat(report.format()).isEqualTo(BookFormat.MARKDOWN);
        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.segments()).isEqualTo(2);
        assertThat(report.accepted()).isEqualTo(1);
        assertThat(report.flagged()).isEqualTo(1);
        assertThat(report.flaggedSegments()).containsExactly(flagged);
        assertThat(report.written()).isEqualTo(Path.of("Book.uk.md"));
        assertThat(report.error()).isNull();
    }
}
