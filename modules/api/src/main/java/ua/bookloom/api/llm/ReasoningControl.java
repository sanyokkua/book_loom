package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How a model's thinking is switched, as its profile resolved it.
 *
 * @param kind what kind of switch the model has; non-null
 * @param offered the values the model or its server offers ("true", "false", "low", "high"); never null, may be empty
 * @param offValue the value that turns thinking off, or its lowest level where it cannot be turned off ("false",
 *     "none", "low"); null when the model has no switch
 * @param serverDefault the value the server uses when none is sent, or null when unknown
 */
public record ReasoningControl(
        Kind kind,
        List<String> offered,
        @Nullable String offValue,
        @Nullable String serverDefault) {

    /** No switch at all: the model never thinks, so no control is ever sent. */
    public static final ReasoningControl NONE = new ReasoningControl(Kind.NONE, List.of(), null, null);

    /** Nothing is known about the switch, so no control is sent. */
    public static final ReasoningControl UNKNOWN = new ReasoningControl(Kind.UNKNOWN, List.of(), null, null);

    /** What kind of thinking switch a model has. */
    public enum Kind {
        /** The model does not think and takes no control. */
        NONE,
        /** On or off. */
        BOOLEAN,
        /** Named levels such as low, medium and high. */
        LEVELS,
        /** It always thinks by design and has no switch. */
        ALWAYS,
        /** Whether it has a switch is not known, so none is sent. */
        UNKNOWN,
        /** It thinks by default and its server gives no way to turn that off, so none is sent. */
        UNSWITCHABLE
    }

    public ReasoningControl {
        Objects.requireNonNull(kind, "kind");
        offered = List.copyOf(Objects.requireNonNull(offered, "offered"));
    }
}
