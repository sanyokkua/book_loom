package ua.bookloom.ui.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The activity-log lines a run queued since the last tick. Never more than the mirror shows: if ticks stall while the
 * engine keeps talking, the oldest lines go first, as they would scroll off the screen anyway.
 *
 * <p>Not thread-safe: {@link RunSession} uses it under its publish lock.
 */
final class PendingLines {

    private final Deque<LogEntry> lines = new ArrayDeque<>();

    void add(final LogEntry line) {
        lines.addLast(Objects.requireNonNull(line, "line"));
        if (lines.size() > StateMirror.MAX_LOG_ENTRIES) {
            lines.removeFirst();
        }
    }

    /**
     * Takes every queued line, oldest first.
     *
     * @return never null; empty when nothing was queued
     */
    List<LogEntry> drain() {
        final List<LogEntry> taken = List.copyOf(lines);
        lines.clear();
        return taken;
    }

    /**
     * How many lines wait.
     *
     * @return never more than {@link StateMirror#MAX_LOG_ENTRIES}
     */
    int size() {
        return lines.size();
    }
}
