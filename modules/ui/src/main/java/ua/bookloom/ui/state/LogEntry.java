package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * One line of the activity log: a kind and the arguments its catalogue message is formatted with.
 *
 * <p>The arguments are {@code String}s so that a segment id is never rendered like a number, with grouping
 * separators.
 *
 * @param kind decides the message, the status role and the mark
 * @param args the message arguments in catalogue order; copied, so a later edit cannot rewrite a line already logged
 */
public record LogEntry(LogKind kind, List<String> args) {

    /** Rejects a missing kind or arguments and takes an unmodifiable copy of the arguments. */
    public LogEntry {
        Objects.requireNonNull(kind, "kind");
        args = List.copyOf(Objects.requireNonNull(args, "args"));
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
