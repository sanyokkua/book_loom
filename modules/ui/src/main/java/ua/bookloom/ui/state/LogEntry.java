package ua.bookloom.ui.state;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * One line of the activity log: a kind, the arguments its catalogue message is formatted with, when it happened and how
 * many times in a row it happened.
 *
 * <p>The arguments are {@code String}s so that a segment id is never rendered like a number, with grouping
 * separators.
 *
 * @param kind decides the message, the status role and the mark
 * @param args the message arguments in catalogue order; copied, so a later edit cannot rewrite a line already logged
 * @param time the wall-clock time of the latest occurrence, or {@code null} when none was recorded
 * @param repeats how many times in a row the same line happened, at least one
 */
public record LogEntry(
        LogKind kind, List<String> args, @Nullable LocalTime time, int repeats) {

    /** Rejects a missing kind or arguments or a count below one, and takes an unmodifiable copy of the arguments. */
    public LogEntry {
        Objects.requireNonNull(kind, "kind");
        args = List.copyOf(Objects.requireNonNull(args, "args"));
        if (repeats < 1) {
            throw new IllegalArgumentException("repeats must be positive: " + repeats);
        }
    }

    /**
     * Builds a line that happened once, with no time recorded.
     *
     * @param kind decides the message, the status role and the mark
     * @param args the message arguments in catalogue order
     */
    public LogEntry(final LogKind kind, final List<String> args) {
        this(kind, args, null, 1);
    }

    /**
     * The same line stamped with a time.
     *
     * @param at when it happened
     * @return a copy with {@code time} set and the count unchanged
     */
    public LogEntry at(final LocalTime at) {
        return new LogEntry(kind, args, Objects.requireNonNull(at, "at"), repeats);
    }

    /**
     * Whether {@code other} says the same thing, whenever it happened.
     *
     * @param other the line to compare
     * @return {@code true} if the kind and the arguments are equal, {@code false} otherwise
     */
    public boolean saysTheSameAs(final LogEntry other) {
        return kind == other.kind && args.equals(other.args);
    }

    /**
     * This line happening once more, at {@code later}'s time.
     *
     * @param later the newer occurrence of the same line
     * @return a copy counting one more occurrence, stamped with the newer time
     */
    public LogEntry repeatedBy(final LogEntry later) {
        return new LogEntry(kind, args, later.time() == null ? time : later.time(), repeats + later.repeats());
    }

    /**
     * Without its time and count, which is what a test or a comparison of wording needs.
     *
     * @return a copy with no time and a count of one
     */
    public LogEntry wording() {
        return new LogEntry(kind, args);
    }

    /**
     * The status role of this entry's kind.
     *
     * @return the role the entry is drawn in
     */
    public StatusRole role() {
        return kind.role();
    }

    /**
     * The catalogue message of this entry's kind.
     *
     * @return the key the entry is worded by
     */
    public MessageKey messageKey() {
        return kind.messageKey();
    }
}
