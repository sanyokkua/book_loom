package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.SCRIPT;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.checks.NonProse;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/**
 * The target-script check: whether the target's letters are written in the target language's own script — the only
 * reliable signal with no language detector in play (ADR-0037).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScriptCheck {

    private static final int SOURCE_FLOOR_CODE_POINTS = 20;
    private static final double SHARE_FAIL_THRESHOLD = 0.60;
    private static final double SHARE_MARGIN_WINDOW = 0.20;

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        if (ForeignMarking.isMarked(input) || NonProse.isLocatorOnly(input.sourceDisplayText())) {
            return CheckResult.skip(SCRIPT);
        }
        final Optional<Script> targetScript = Languages.scriptOf(input.targetLanguage());
        final Optional<Script> sourceScript = Languages.scriptOf(input.sourceLanguage());
        if (targetScript.isEmpty()) {
            log.debug("script check skipped: target {} has no known script", input.targetLanguage());
            return CheckResult.skip(SCRIPT);
        }
        if (sameScript(sourceScript, targetScript)) {
            log.debug("script check skipped: source and target {} share a script", targetScript.get());
            return CheckResult.skip(SCRIPT);
        }
        final String source = NameRemoval.sourceWithoutNames(input);
        if (codePointCount(source) < SOURCE_FLOOR_CODE_POINTS) {
            log.debug(
                    "script check skipped: source has {} code points, under {}",
                    codePointCount(source),
                    SOURCE_FLOOR_CODE_POINTS);
            return CheckResult.skip(SCRIPT);
        }
        final String target = NameRemoval.targetWithoutNames(input);
        final OptionalDouble share = shareInScript(target, targetScript.get());
        if (share.isEmpty()) {
            return CheckResult.skip(SCRIPT);
        }
        return outcome(share.getAsDouble());
    }

    private static CheckResult outcome(final double share) {
        if (share < SHARE_FAIL_THRESHOLD) {
            return CheckResult.fail(SCRIPT, "target-script share " + share + " below " + SHARE_FAIL_THRESHOLD);
        }
        return CheckResult.pass(SCRIPT, Margins.clamp((share - SHARE_FAIL_THRESHOLD) / SHARE_MARGIN_WINDOW));
    }

    private static boolean sameScript(final Optional<Script> source, final Optional<Script> target) {
        return source.isPresent() && target.isPresent() && source.get() == target.get();
    }

    private static int codePointCount(final String text) {
        return text.codePointCount(0, text.length());
    }

    /** The share of {@code text}'s letters written in {@code script}, or empty when {@code text} has no letter. */
    private static OptionalDouble shareInScript(final String text, final Script script) {
        final long total = text.codePoints().filter(Character::isLetter).count();
        if (total == 0) {
            return OptionalDouble.empty();
        }
        final long inScript = text.codePoints()
                .filter(codePoint -> Character.isLetter(codePoint)
                        && script.letterScripts().contains(Character.UnicodeScript.of(codePoint)))
                .count();
        return OptionalDouble.of((double) inScript / total);
    }
}
