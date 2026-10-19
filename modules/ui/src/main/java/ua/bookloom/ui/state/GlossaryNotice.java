package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * A line the names and style screen shows in place, above the glossary.
 *
 * @param level whether the line reports a failure or merely a result
 * @param text the wording, already in the display language
 */
public record GlossaryNotice(Level level, String text) {

    /** How the line is painted. */
    public enum Level {
        /** A result the person asked for. */
        INFO,
        /** Something that did not work. */
        ERROR
    }

    /** Validates the invariants a caller is entitled to assume. */
    public GlossaryNotice {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(text, "text");
    }
}
