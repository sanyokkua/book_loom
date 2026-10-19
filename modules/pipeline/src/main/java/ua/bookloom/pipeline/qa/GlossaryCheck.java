package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.GLOSSARY;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.WholeWord;

/**
 * The glossary-compliance check: whether every locked term present in the segment appears, as a whole word, as its
 * entered rendering in the masked form. A locked term travels as a token, so this holds whenever the protected-span
 * gate passed; it still stays in the confidence blend because the reference weights include it, and it catches a
 * rendering that only appears inside a longer word.
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
                .allMatch(locked -> WholeWord.pattern(locked.rendering())
                        .matcher(input.targetWithRenderings())
                        .find());
        if (!allRendered) {
            return CheckResult.fail(GLOSSARY, "a locked term's rendering is missing from the target");
        }
        return CheckResult.pass(GLOSSARY, FULL_MARGIN);
    }
}
