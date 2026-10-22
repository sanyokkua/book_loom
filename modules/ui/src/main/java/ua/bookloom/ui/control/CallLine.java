package ua.bookloom.ui.control;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunRow;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.WaitingCall;

/**
 * The words of the live card's call line: the run-state row's live line, "Waiting for the model… 0:12" once a call has
 * waited ten seconds, and in Detailed the call itself ("Call 42 · Draft · 4 segments · ch2 · p05–p08 · attempt 1 of 2
 * · 0:21 of 3:00") while it translates. A stuck call keeps its sentence in both views; its one clock is apart ({@link #stuckClock}).
 * Pure: it runs on every tick, so it logs nothing.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallLine {

    /** How long a call waits before the line says so. */
    static final Duration SAY_WAITING_AFTER = Duration.ofSeconds(10);

    private static final String NONE = "none";
    private static final String RANGE = "–";
    private static final String PART = " · ";
    private static final long SECONDS_CAP = Integer.MAX_VALUE;

    /**
     * The line.
     *
     * @param view the run-state row
     * @param waiting the call that waits, or {@code null}
     * @param current the call in flight or the newest finished one, or {@code null}
     * @param asOf the moment a waiting call's clock reads, or {@code null}
     * @param detailed whether the Detailed view is chosen
     * @param messages the catalogue
     * @return the text, never null
     */
    public static String of(
            final RunStateView view,
            final @Nullable WaitingCall waiting,
            final @Nullable CallSnapshot current,
            final @Nullable Instant asOf,
            final boolean detailed,
            final Messages messages) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(messages, "messages");
        if (view.row() == RunRow.STUCK) {
            return view.liveLine().in(messages);
        }
        if (detailed && current != null && view.row() == RunRow.TRANSLATING) {
            return callLine(current, asOf, messages);
        }
        if (view.row() == RunRow.TRANSLATING
                && waiting != null
                && waiting.totalWaited().compareTo(SAY_WAITING_AFTER) >= 0) {
            return messages.get(MessageKey.LIVE_WAITING_FOR_MODEL, clockOf(waiting.totalWaited()));
        }
        return view.liveLine().in(messages);
    }

    /**
     * The one clock of a stuck call: how long this attempt has waited against its limit.
     *
     * @param view the run-state row
     * @param waiting the call that waits, or {@code null}
     * @param messages the catalogue
     * @return the clock, such as {@code 3:40 of 6:00}, or empty when the call is not stuck
     */
    public static String stuckClock(
            final RunStateView view, final @Nullable WaitingCall waiting, final Messages messages) {
        if (view.row() != RunRow.STUCK || waiting == null) {
            return "";
        }
        final Duration limit = waiting.timeout();
        final String waited = clockOf(waiting.attemptWaited());
        return limit == null ? waited : messages.get(MessageKey.LIVE_CLOCK_OF, waited, clockOf(limit));
    }

    private static String callLine(final CallSnapshot call, final @Nullable Instant asOf, final Messages messages) {
        return messages.get(
                MessageKey.LIVE_CALL_LINE,
                String.valueOf(call.callId()),
                kindName(call.kind(), messages),
                call.segments().size(),
                rangeOf(call.segments()),
                String.valueOf(call.attempt()),
                String.valueOf(call.maxAttempts()),
                clock(call, asOf, messages));
    }

    private static String kindName(final CallKind kind, final Messages messages) {
        return kind == CallKind.DRAFT
                ? messages.get(MessageKey.LIVE_CALL_DRAFT)
                : messages.get(MessageKey.RUN_CALL_KIND, WaitingCall.tokenOf(kind));
    }

    private static String clock(final CallSnapshot call, final @Nullable Instant asOf, final Messages messages) {
        if (call.state() != CallState.WAITING) {
            return messages.get(MessageKey.LIVE_CALL_TOOK, clockOf(call.elapsed()));
        }
        final String waited = clockOf(asOf == null ? Duration.ZERO : Duration.between(call.startedAt(), asOf));
        final Duration limit = call.timeout();
        return limit == null ? waited : messages.get(MessageKey.LIVE_CLOCK_OF, waited, clockOf(limit));
    }

    // "ch2 · p05–p08" when the first and last share their chapter, else both locators whole.
    static String rangeOf(final List<CallSegment> segments) {
        if (segments.isEmpty()) {
            return NONE;
        }
        final String first = segments.getFirst().locator();
        final String last = segments.getLast().locator();
        if (segments.size() == 1 || first.equals(last)) {
            return first;
        }
        final int cut = first.lastIndexOf(PART);
        final String prefix = cut < 0 ? "" : first.substring(0, cut + PART.length());
        return !prefix.isEmpty() && last.startsWith(prefix)
                ? first + RANGE + last.substring(prefix.length())
                : first + RANGE + last;
    }

    private static String clockOf(final Duration length) {
        return DurationText.clock((int) Math.min(Math.max(length.toSeconds(), 0), SECONDS_CAP));
    }
}
