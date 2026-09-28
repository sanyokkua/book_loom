package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.ECHO;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The untranslated-echo check: whether the target merely copies its source back, with the echo floor (owner
 * decision) that keeps a short line — a name, a numeral, "OK" — from blocking acceptance on a failed echo alone.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EchoCheck {

    private static final double SIMILARITY_FAIL_THRESHOLD = 0.90;
    private static final double SIMILARITY_MARGIN_WINDOW = 0.10;
    private static final int ECHO_FLOOR_CODE_POINTS = 20;

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        if (ForeignMarking.isMarked(input)) {
            return CheckResult.skip(ECHO);
        }
        final String source = NameRemoval.sourceWithoutNames(input);
        final int sourceCodePoints = source.codePointCount(0, source.length());
        if (sourceCodePoints == 0) {
            return CheckResult.skip(ECHO);
        }
        final String target = NameRemoval.targetWithoutNames(input);
        final double similarity = TextSimilarity.similarity(source, target);
        if (similarity < SIMILARITY_FAIL_THRESHOLD) {
            return CheckResult.pass(
                    ECHO, Margins.clamp((SIMILARITY_FAIL_THRESHOLD - similarity) / SIMILARITY_MARGIN_WINDOW));
        }
        return failed(sourceCodePoints, similarity);
    }

    private static CheckResult failed(final int sourceCodePoints, final double similarity) {
        final String note = "echo similarity " + similarity + " at or above " + SIMILARITY_FAIL_THRESHOLD;
        if (sourceCodePoints < ECHO_FLOOR_CODE_POINTS) {
            return CheckResult.failNonBlocking(ECHO, note);
        }
        return CheckResult.fail(ECHO, note);
    }
}
