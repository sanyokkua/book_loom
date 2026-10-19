package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The five deterministic soft checks, run in one fixed order per segment — script, echo, repetition, length, then
 * glossary — so a caller always reads {@link CheckResult}s in a predictable sequence.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoftChecks {

    /**
     * Runs every soft check against one segment's restored candidate.
     *
     * @param input the segment's display texts, languages and policies
     * @return the five checks' outcomes, in the order script, echo, repetition, length, glossary
     */
    public static List<CheckResult> run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        return List.of(
                ScriptCheck.run(input),
                EchoCheck.run(input),
                RepetitionCheck.run(input),
                LengthCheck.run(input),
                GlossaryCheck.run(input));
    }
}
