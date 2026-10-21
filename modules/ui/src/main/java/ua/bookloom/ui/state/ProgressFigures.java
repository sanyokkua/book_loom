package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Reads a progress dialog's figures from the steps its work announced. A seam, so a dialog's figures come from a
 * {@link ProgressLedger} without the dialog knowing it.
 */
@FunctionalInterface
public interface ProgressFigures {

    /** The percentage a finished share reads as, and the one an unfinished share never reaches. */
    int FULL = 100;

    /**
     * The figures for the steps so far.
     *
     * @param steps what the work announced
     * @param now the moment the figures are read at
     * @return the figures; never null
     */
    Figures of(StepLog steps, Instant now);

    /**
     * Makes a reader for each piece of work, because a reader keeps what it measured of one piece only.
     */
    @FunctionalInterface
    interface Factory {

        /** {@return a reader that has seen no steps} */
        ProgressFigures create();
    }

    /**
     * What one kind of step adds to the count.
     *
     * @param kind what the steps are
     * @param planned the steps of the kind planned, at least {@code done}
     * @param done the steps of the kind finished
     * @param secondsPerStep the measured seconds a step of the kind takes, or {@code null} before one was timed
     */
    record KindFigures(
            StepKind kind, int planned, int done, @Nullable Double secondsPerStep) {

        /** Rejects a missing kind. */
        public KindFigures {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * What the figures line of a progress dialog and the bars of a run say.
     *
     * @param percent the share of the steps done, 0 to 100; 100 only when every step is done
     * @param done the steps done
     * @param total the steps planned or announced, at least {@code done}
     * @param left the time left, or {@code null} while it is not known
     * @param fraction the share the bar shows, between 0 and 1, which never falls
     * @param kinds the steps per kind, for the note on how progress is counted; empty when the work has no kinds
     */
    record Figures(
            int percent, int done, int total, @Nullable Duration left, double fraction, List<KindFigures> kinds) {

        /** The figures of work that has done nothing yet. */
        public static final Figures NONE = new Figures(0, 0, 0, null);

        /** Freezes the kinds. */
        public Figures {
            kinds = List.copyOf(Objects.requireNonNull(kinds, "kinds"));
        }

        /**
         * Figures with no kinds, whose bar is the percentage.
         *
         * @param percent the share of the steps done, 0 to 100
         * @param done the steps done
         * @param total the steps planned or announced
         * @param left the time left, or {@code null} while it is not known
         */
        public Figures(final int percent, final int done, final int total, final @Nullable Duration left) {
            this(percent, done, total, left, percent / (double) FULL, List.of());
        }

        /**
         * The share of {@code done} of {@code total} as the bar and the title read it.
         *
         * @param done the steps done, not negative
         * @param total the steps planned, at least {@code done}
         * @return the figures of a plain count, without a time left
         */
        public static Figures ofCount(final int done, final int total) {
            final double fraction = total == 0 ? 0 : (double) done / total;
            return new Figures(percentOf(fraction, done >= total && total > 0), done, total, null, fraction, List.of());
        }

        /**
         * The percentage a share reads as: rounded, and below 100 until the work is finished.
         *
         * @param fraction the share, between 0 and 1
         * @param finished whether every step is done
         * @return the percent from 0 to 100
         */
        public static int percentOf(final double fraction, final boolean finished) {
            return finished ? FULL : Math.min(FULL - 1, (int) Math.round(fraction * FULL));
        }

        /**
         * The same figures at another percentage and bar, for a reader that must not let the bar fall.
         *
         * @param held the percentage to keep
         * @param heldFraction the bar to keep
         * @return these figures with the held share
         */
        public Figures holding(final int held, final double heldFraction) {
            return new Figures(held, done, total, left, heldFraction, kinds);
        }

        /**
         * Words the line, such as "51 % · 26 of about 49 steps · Left about 1 min".
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
                    : messages.get(MessageKey.PROGRESS_FIGURES_LEFT, counts, DurationText.about(messages, remaining));
        }
    }
}
