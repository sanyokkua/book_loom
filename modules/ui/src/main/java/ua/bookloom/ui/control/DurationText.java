package ua.bookloom.ui.control;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * How a length of time reads: the coarse {@code 1h 02m} of the title bar, which the catalogue words per language, and
 * the {@code m:ss} clock of the waiting banner, which is the same in every language.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DurationText {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long MINUTES_PER_HOUR = 60;

    /**
     * Words a duration in its largest unit: hours with two-digit minutes from one hour, whole minutes from one minute,
     * seconds below.
     *
     * @param messages the catalogue that words the units in the display language
     * @param duration a non-negative length of time; the part below a second is dropped
     * @return the text, such as {@code 1h 02m}, {@code 12m} or {@code 45s}
     */
    public static String format(final Messages messages, final Duration duration) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(duration, "duration");
        final long seconds = duration.toSeconds();
        final long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes >= MINUTES_PER_HOUR) {
            return messages.get(
                    MessageKey.DURATION_HM,
                    Long.toString(minutes / MINUTES_PER_HOUR),
                    String.format(Locale.ROOT, "%02d", minutes % MINUTES_PER_HOUR));
        }
        if (minutes >= 1) {
            return messages.get(MessageKey.DURATION_M, Long.toString(minutes));
        }
        return messages.get(MessageKey.DURATION_S, Long.toString(seconds));
    }

    /**
     * The waiting clock.
     *
     * @param seconds whole seconds, not negative
     * @return {@code m:ss}, with minutes unbounded, such as {@code 1:15}
     */
    public static String clock(final int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
    }
}
