package ua.bookloom.pipeline.qa;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Blends the five soft checks' margins into one confidence number, so the acceptance rule has a single deterministic
 * score with no reviewer opinion mixed in (design D8).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Confidence {

    /**
     * The fixed summation order — glossary, length, script, echo, repetition — so the blend is byte-identical
     * whatever order the caller's results arrive in (floating-point addition is not associative).
     */
    private static final List<CheckName> BLEND_ORDER =
            List.of(CheckName.GLOSSARY, CheckName.LENGTH, CheckName.SCRIPT, CheckName.ECHO, CheckName.REPETITION);

    /**
     * Blends a set of soft-check results into one confidence value.
     *
     * @param results the soft-check results to blend; a hard-gate result is ignored (its {@link CheckName#weight()}
     *     is {@code 0.0}), and a {@link CheckName} missing from {@code results} contributes {@code 0.0}
     * @return the sum, in {@link #BLEND_ORDER}, of each check's {@link CheckName#weight()} times its margin
     */
    static double blend(final List<CheckResult> results) {
        Objects.requireNonNull(results, "results");
        final Map<CheckName, Double> marginByCheck = marginsByCheck(results);
        double confidence = 0.0;
        for (final CheckName check : BLEND_ORDER) {
            confidence += check.weight() * marginByCheck.getOrDefault(check, 0.0);
        }
        return confidence;
    }

    private static Map<CheckName, Double> marginsByCheck(final List<CheckResult> results) {
        final Map<CheckName, Double> margins = new EnumMap<>(CheckName.class);
        for (final CheckResult result : results) {
            margins.put(result.check(), result.margin());
        }
        return margins;
    }
}
