package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Reads a progress dialog's figures from the steps its work announced. A seam, so the share of steps can later be
 * weighed by the time measured per kind of step and give a time left without the dialog changing.
 */
@FunctionalInterface
public interface ProgressFigures {

    /**
     * The figures for the steps so far.
     *
     * @param steps what the work announced
     * @param now the moment the figures are read at
     * @return the figures; never null
     */
    Figures of(StepLog steps, Instant now);

    /**
     * What the figures line of a progress dialog says.
     *
     * @param percent the share of the steps done, 0 to 100
     * @param done the steps done
     * @param total the steps planned or announced, at least {@code done}
     * @param left the time left, or {@code null} while it is not known
     */
    record Figures(
            int percent, int done, int total, @Nullable Duration left) {

        /** The figures of work that has done nothing yet. */
        public static final Figures NONE = new Figures(0, 0, 0, null);

        /**
         * Words the line, such as "51 % · 26 of about 49 steps · Left about 1m".
         *
         * @param messages the catalogue the line is worded from
         * @return the line; never null
         */
        public String line(final Messages messages) {
            Objects.requireNonNull(messages, "messages");
            final String counts = messages.get(MessageKey.PROGRESS_FIGURES, percent, done, total);
            final Duration remaining = left;
            return remaining == null
                    ? counts
                    : messages.get(MessageKey.PROGRESS_FIGURES_LEFT, counts, DurationText.format(messages, remaining));
        }
    }
}
