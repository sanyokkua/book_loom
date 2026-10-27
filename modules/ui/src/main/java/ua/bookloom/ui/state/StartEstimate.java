package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What the quality choice may say about time before a translation starts: hours only when a speed was measured on this
 * computer, otherwise the relative cost of the choices, which is true without knowing the computer. A guess of hours
 * with nothing measured behind it would be believed. {@link StartEstimates} makes one.
 */
public sealed interface StartEstimate {

    /**
     * The words under the quality choice.
     *
     * @param messages the catalogue the lines are worded from
     * @return one line with the measured time, marked as an estimate; or the two relative costs
     */
    List<String> lines(Messages messages);

    /**
     * A time measured on this computer.
     *
     * @param forBook the time the book is expected to take at the chosen quality, not rounded
     * @param source where the speed it is worked out from was measured
     */
    record Measured(Duration forBook, MeasuredFrom source) implements StartEstimate {

        /** Rejects a missing time or source. */
        public Measured {
            Objects.requireNonNull(forBook, "forBook");
            Objects.requireNonNull(source, "source");
        }

        @Override
        public List<String> lines(final Messages messages) {
            final MessageKey line =
                    source == MeasuredFrom.RUN ? MessageKey.ESTIMATE_MEASURED_RUN : MessageKey.ESTIMATE_MEASURED;
            return List.of(messages.get(line, DurationText.about(messages, TimeLeftShaper.round(forBook))));
        }

        /**
         * The hover explanation of the line, which says where the speed was measured.
         *
         * @return the key of the explanation
         */
        public MessageKey tip() {
            return source == MeasuredFrom.RUN ? MessageKey.ESTIMATE_MEASURED_RUN_TIP : MessageKey.ESTIMATE_MEASURED_TIP;
        }
    }

    /** The cost of the choices against Fast, which needs no measurement. */
    record Relative() implements StartEstimate {

        @Override
        public List<String> lines(final Messages messages) {
            return List.of(
                    messages.get(MessageKey.ESTIMATE_RELATIVE_BALANCED),
                    messages.get(MessageKey.ESTIMATE_RELATIVE_MAX));
        }
    }
}
