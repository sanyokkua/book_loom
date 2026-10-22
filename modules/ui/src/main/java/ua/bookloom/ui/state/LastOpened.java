package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Words when a project was last opened by calendar days of the clock's zone: today, yesterday, or the date. */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class LastOpened {

    private final Clock clock;
    private final Messages messages;

    /**
     * Words an instant against today.
     *
     * @param opened when the project was last opened; a moment in the future, from a clock that moved back, is today
     * @return "today", "yesterday" or the day and month, in the display language
     */
    public String text(final Instant opened) {
        Objects.requireNonNull(opened, "opened");
        final ZoneId zone = clock.getZone();
        final LocalDate day = opened.atZone(zone).toLocalDate();
        final LocalDate today = LocalDate.now(clock);
        if (!day.isBefore(today)) {
            return messages.get(MessageKey.PROJECTS_WHEN_TODAY);
        }
        if (day.equals(today.minusDays(1))) {
            return messages.get(MessageKey.PROJECTS_WHEN_YESTERDAY);
        }
        // The date pattern formats in the JVM's zone, so the day is handed over as that zone's midnight.
        return messages.get(
                MessageKey.PROJECTS_WHEN_DATE,
                Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }
}
