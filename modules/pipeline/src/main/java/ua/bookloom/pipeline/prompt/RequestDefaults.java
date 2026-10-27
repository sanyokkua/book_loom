package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import ua.bookloom.api.llm.ReasoningLevel;

/**
 * The reasoning plan and sampling mode every request of a run is built with, resolved once from the environment and
 * the stored Generation settings so a run does not change its mind part-way.
 *
 * @param reasoning the level each call asks for
 * @param sampling whether calls send the tuned sampling profile or leave sampling to the server
 */
public record RequestDefaults(ReasoningPlan reasoning, SamplingMode sampling) {

    public RequestDefaults {
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(sampling, "sampling");
    }

    /**
     * The defaults the environment variables and properties alone give, with nothing stored consulted.
     *
     * @return the defaults of this process
     */
    public static RequestDefaults fromEnvironment() {
        return new RequestDefaults(ReasoningPlan.current(), SamplingMode.current());
    }

    /**
     * The level a call asks for.
     *
     * @param name the call; never null
     * @return the level, never null
     */
    public ReasoningLevel reasoningFor(final PromptName name) {
        return reasoning.levelFor(name);
    }
}
