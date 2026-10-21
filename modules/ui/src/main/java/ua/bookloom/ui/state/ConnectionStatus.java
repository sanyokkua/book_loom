package ua.bookloom.ui.state;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * How the model server has been answering, for the hover explanation of the title bar's AI-model chip; the chip's own
 * words and colour come from the run-state row.
 *
 * @param sinceLastAnswer how long ago the last model call was answered, in whole seconds, or {@code null} before any
 * @param timeoutsRecently how many attempts timed out in the last ten minutes
 * @param failuresRecently how many attempts failed in the last ten minutes, timeouts included
 * @param draftTokensPerSecond the recent drafting speed, or {@code null} before one is known
 * @param judgeTokensPerSecond the recent judging speed, or {@code null} before one is known
 */
public record ConnectionStatus(
        @Nullable Duration sinceLastAnswer,
        int timeoutsRecently,
        int failuresRecently,
        @Nullable Double draftTokensPerSecond,
        @Nullable Double judgeTokensPerSecond) {

    /** The status before a run has made any call. */
    public static final ConnectionStatus UNKNOWN = new ConnectionStatus(null, 0, 0, null, null);
}
