package ua.bookloom.ui.state;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * One line of the activity log: a kind, the catalogue message and the arguments it is formatted with, when it happened,
 * how many times in a row it happened, and whether it is a technical line that only Show technical details lists.
 *
 * <p>The arguments are {@code String}s so that a segment id is never rendered like a number, with grouping
 * separators. The kind decides the tag, the mark and the colour; the message may be another than the kind's own, so a
 * failed call reads as a call while it is tagged as a retry.
 *
 * @param kind decides the tag, the status role and the mark
 * @param messageKey the catalogue message the line is worded by
 * @param args the message arguments in catalogue order; copied, so a later edit cannot rewrite a line already logged
 * @param time the wall-clock time of the latest occurrence, or {@code null} when none was recorded
 * @param repeats how many times in a row the same line happened, at least one
 * @param technical {@code true} for a line listed only while Show technical details is on
 */
public record LogEntry(
        LogKind kind,
        MessageKey messageKey,
        List<String> args,
        @Nullable LocalTime time,
        int repeats,
        boolean technical) {

    // The messages whose first argument is a call kind's token, which the log words by the catalogue's call names.
    private static final Set<MessageKey> CALL_MESSAGES =
            Set.of(MessageKey.LOG_MODEL_CALL, MessageKey.LOG_CALL_FAILED, MessageKey.LOG_CALL_PAUSED);

    /** Rejects a missing part or a count below one, and takes an unmodifiable copy of the arguments. */
    public LogEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(messageKey, "messageKey");
        args = List.copyOf(Objects.requireNonNull(args, "args"));
        if (repeats < 1) {
            throw new IllegalArgumentException("repeats must be positive: " + repeats);
        }
    }

    /**
     * Builds a line worded by its kind's own message, that happened once, with no time recorded.
     *
     * @param kind decides the message, the status role and the mark
     * @param args the message arguments in catalogue order
     */
    public LogEntry(final LogKind kind, final List<String> args) {
        this(kind, args, null, 1);
    }

    /**
     * Builds a line worded by its kind's own message.
     *
     * @param kind decides the message, the status role and the mark
     * @param args the message arguments in catalogue order
     * @param time when it happened, or {@code null}
     * @param repeats how many times in a row it happened, at least one
     */
    public LogEntry(final LogKind kind, final List<String> args, final @Nullable LocalTime time, final int repeats) {
        this(kind, Objects.requireNonNull(kind, "kind").messageKey(), args, time, repeats, false);
    }

    /**
     * A line every reader sees, worded by a message of its own.
     *
     * @param kind decides the tag, the status role and the mark
     * @param message the catalogue message
     * @param args the message arguments in catalogue order
     * @return the line, once, with no time
     */
    public static LogEntry of(final LogKind kind, final MessageKey message, final List<String> args) {
        return new LogEntry(kind, message, args, null, 1, false);
    }

    /**
     * A line listed only while Show technical details is on.
     *
     * @param kind decides the tag, the status role and the mark
     * @param message the catalogue message
     * @param args the message arguments in catalogue order
     * @return the line, once, with no time
     */
    public static LogEntry technical(final LogKind kind, final MessageKey message, final List<String> args) {
        return new LogEntry(kind, message, args, null, 1, true);
    }

    /**
     * The same line stamped with a time.
     *
     * @param at when it happened
     * @return a copy with {@code time} set and the count unchanged
     */
    public LogEntry at(final LocalTime at) {
        return new LogEntry(kind, messageKey, args, Objects.requireNonNull(at, "at"), repeats, technical);
    }

    /**
     * Whether {@code other} says the same thing, whenever it happened.
     *
     * @param other the line to compare
     * @return {@code true} if the kind, the message, the arguments and the technical mark are equal
     */
    public boolean saysTheSameAs(final LogEntry other) {
        return kind == other.kind
                && messageKey == other.messageKey
                && technical == other.technical
                && args.equals(other.args);
    }

    /**
     * This line happening once more, at {@code later}'s time.
     *
     * @param later the newer occurrence of the same line
     * @return a copy counting one more occurrence, stamped with the newer time
     */
    public LogEntry repeatedBy(final LogEntry later) {
        return new LogEntry(
                kind,
                messageKey,
                args,
                later.time() == null ? time : later.time(),
                repeats + later.repeats(),
                technical);
    }

    /**
     * Without its time and count, which is what a test or a comparison of wording needs.
     *
     * @return a copy with no time and a count of one
     */
    public LogEntry wording() {
        return new LogEntry(kind, messageKey, args, null, 1, technical);
    }

    /**
     * The status role of this entry's kind.
     *
     * @return the role the entry is drawn in
     */
    public Tone role() {
        return kind.role();
    }

    /**
     * Whether the entry's first argument is a call kind's token, which the log words by the catalogue's call names.
     *
     * @return {@code true} for a model call's line, {@code false} otherwise
     */
    public boolean namesACall() {
        return CALL_MESSAGES.contains(messageKey);
    }
}
