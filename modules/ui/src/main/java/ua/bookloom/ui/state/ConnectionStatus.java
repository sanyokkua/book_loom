package ua.bookloom.ui.state;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * How the model server has been answering, for the status bar's connection chip.
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

    /** How the chip is coloured: by the recent failures first, then by whether anything was answered at all. */
    public enum Health {
        /** Nothing has been answered yet; the chip is drawn in the title bar's own colour. */
        UNKNOWN("conn-unknown"),
        /** The server answers and nothing failed lately; the chip is drawn in the success role. */
        STEADY("conn-steady"),
        /** Some attempts failed or timed out lately; the chip is drawn in the warning role. */
        UNSTEADY("conn-unsteady");

        private final String styleClass;

        Health(final String styleClass) {
            this.styleClass = styleClass;
        }

        /**
         * The style class the chip is painted by.
         *
         * @return a non-blank class name
         */
        public String styleClass() {
            return styleClass;
        }
    }

    /**
     * The chip's colour band.
     *
     * @return {@link Health#UNSTEADY} after a recent failure, {@link Health#UNKNOWN} before any answer, else
     *     {@link Health#STEADY}
     */
    public Health health() {
        if (failuresRecently > 0) {
            return Health.UNSTEADY;
        }
        return sinceLastAnswer == null ? Health.UNKNOWN : Health.STEADY;
    }
}
