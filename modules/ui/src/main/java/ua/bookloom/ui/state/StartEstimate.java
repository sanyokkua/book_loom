package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What the quality choice may say about time before a translation starts (design D3 "Estimates"): a time measured on
 * this model when every kind of call the plan makes was measured by a run, a range marked as an estimate and where
 * its draft speed was measured when only that is known, and nothing when nothing was measured. A guess of hours with
 * nothing measured behind it would be believed. {@link StartEstimates} makes one.
 */
public sealed interface StartEstimate {

    /**
     * The words under the quality choice.
     *
     * @param messages the catalogue the lines are worded from
     * @return one line with the time; never null, empty when nothing was measured
     */
    List<String> lines(Messages messages);

    /**
     * The hover explanation of the line, which says how the time was worked out.
     *
     * @return the key of the explanation, or empty when there is no line
     */
    Optional<MessageKey> tip();

    /**
     * The planned calls of each kind times the seconds this model took for each.
     *
     * @param forBook the time the book is expected to take at the chosen quality, not rounded
     */
    record Measured(Duration forBook) implements StartEstimate {

        /** Rejects a missing time. */
        public Measured {
            Objects.requireNonNull(forBook, "forBook");
        }

        @Override
        public List<String> lines(final Messages messages) {
            return List.of(messages.get(
                    MessageKey.ESTIMATE_MEASURED, DurationText.about(messages, StartEstimates.wholeMinutes(forBook))));
        }

        @Override
        public Optional<MessageKey> tip() {
            return Optional.of(MessageKey.ESTIMATE_MEASURED_TIP);
        }
    }

    /**
     * A range worked out from this model's seconds per drafted segment, since not every kind of call was measured.
     *
     * @param low the start of the range, already rounded down to a whole hour or to five minutes
     * @param high the end of the range, already rounded up the same way
     * @param from where the draft speed was measured
     */
    record Ranged(Duration low, Duration high, MeasuredFrom from) implements StartEstimate {

        /** Rejects a missing part. */
        public Ranged {
            Objects.requireNonNull(low, "low");
            Objects.requireNonNull(high, "high");
            Objects.requireNonNull(from, "from");
        }

        @Override
        public List<String> lines(final Messages messages) {
            final MessageKey line =
                    from == MeasuredFrom.RUN ? MessageKey.ESTIMATE_RANGE_RUN : MessageKey.ESTIMATE_RANGE_TEST;
            final String range = high.compareTo(StartEstimates.HOURS_FROM) >= 0
                    ? messages.get(
                            MessageKey.DURATION_ABOUT_RANGE_H,
                            Long.toString(low.toHours()),
                            Long.toString(high.toHours()))
                    : messages.get(
                            MessageKey.DURATION_ABOUT_RANGE_M,
                            Long.toString(low.toMinutes()),
                            Long.toString(high.toMinutes()));
            return List.of(messages.get(line, range));
        }

        @Override
        public Optional<MessageKey> tip() {
            return Optional.of(
                    from == MeasuredFrom.RUN ? MessageKey.ESTIMATE_RANGE_RUN_TIP : MessageKey.ESTIMATE_RANGE_TEST_TIP);
        }
    }

    /** Nothing was measured for the model, so no time is shown; tokens per second alone give none. */
    record None() implements StartEstimate {

        @Override
        public List<String> lines(final Messages messages) {
            return List.of();
        }

        @Override
        public Optional<MessageKey> tip() {
            return Optional.empty();
        }
    }
}
