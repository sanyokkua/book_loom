package ua.bookloom.llm.profile;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ReasoningControl;

/** The thinking switches the built-in rows share, so each is written once. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RowReasoning {

    /** A model that does not think: no control is ever sent. */
    static final ReasoningControl NONE = ReasoningControl.NONE;

    /** A model that thinks by default and has an on/off switch; the engine's own off value is chosen per engine. */
    static final ReasoningControl BOOLEAN =
            new ReasoningControl(ReasoningControl.Kind.BOOLEAN, List.of("true", "false"), "false", "true");

    /** A model with named levels that is switched off with {@code false} and is never sent {@code true}. */
    static final ReasoningControl LEVELS =
            new ReasoningControl(ReasoningControl.Kind.LEVELS, List.of(), "false", "medium");

    /** The harmony format of gpt-oss: it always reasons and its lowest level is {@code low}. */
    static final ReasoningControl HARMONY =
            new ReasoningControl(ReasoningControl.Kind.ALWAYS, List.of("low", "medium", "high"), "low", "medium");

    /** The muse-glimmer template: it always reasons and its lowest level is {@code low}, never {@code max}. */
    static final ReasoningControl GLIMMER =
            new ReasoningControl(ReasoningControl.Kind.ALWAYS, List.of("low", "high"), "low", "high");

    /** A model that always reasons and has no switch at all: nothing is sent and room is reserved for the reasoning. */
    static final ReasoningControl ALWAYS_NO_SWITCH =
            new ReasoningControl(ReasoningControl.Kind.ALWAYS, List.of(), null, "true");
}
