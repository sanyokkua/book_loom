package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.GLOSSARY;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The glossary-compliance check: whether every locked term present in the segment came back as its entered
 * rendering. Passes whenever the protected-span hard gate did (task 9.3), since a locked term travels as a
 * placeholder; it stays in the confidence blend because the reference weights include it.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryCheck {

    private static final double FULL_MARGIN = 1.0;

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        if (input.lockedRenderings().isEmpty()) {
            return CheckResult.skip(GLOSSARY);
        }
        final boolean allRendered = input.lockedRenderings().stream()
                .allMatch(locked -> input.targetDisplayText().contains(locked.rendering()));
        if (!allRendered) {
            return CheckResult.fail(GLOSSARY, "a locked term's rendering is missing from the target");
        }
        return CheckResult.pass(GLOSSARY, FULL_MARGIN);
    }
}
