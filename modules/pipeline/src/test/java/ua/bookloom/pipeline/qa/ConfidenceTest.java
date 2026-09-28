package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link Confidence#blend(List)}: the fixed weighted sum, and its order-independence. */
class ConfidenceTest {

    private static final String NOTE = "note";
    private static final double HALF_MARGIN = 0.5;

    private static final CheckResult GLOSSARY_SKIP = CheckResult.skip(CheckName.GLOSSARY);
    private static final CheckResult GLOSSARY_PASS = CheckResult.pass(CheckName.GLOSSARY, 1.0);
    private static final CheckResult LENGTH_HALF = CheckResult.pass(CheckName.LENGTH, HALF_MARGIN);
    private static final CheckResult LENGTH_PASS = CheckResult.pass(CheckName.LENGTH, 1.0);
    private static final CheckResult SCRIPT_SKIP = CheckResult.skip(CheckName.SCRIPT);
    private static final CheckResult SCRIPT_PASS = CheckResult.pass(CheckName.SCRIPT, 1.0);
    private static final CheckResult SCRIPT_FAIL = CheckResult.fail(CheckName.SCRIPT, NOTE);
    private static final CheckResult ECHO_PASS = CheckResult.pass(CheckName.ECHO, 1.0);
    private static final CheckResult ECHO_FAIL = CheckResult.fail(CheckName.ECHO, NOTE);
    private static final CheckResult REPETITION_PASS = CheckResult.pass(CheckName.REPETITION, 1.0);

    @ParameterizedTest(name = "{0}")
    @MethodSource("blendCases")
    void blend_softCheckResults_sumsWeightedMargins(
            final String name, final List<CheckResult> results, final double expected) {
        assertThat(Confidence.blend(results)).isCloseTo(expected, within(1e-9));
    }

    private static Stream<Arguments> blendCases() {
        return Stream.of(
                Arguments.of(
                        "no locked term, a half-margin length and the rest full",
                        List.of(GLOSSARY_SKIP, LENGTH_HALF, SCRIPT_PASS, ECHO_PASS, REPETITION_PASS),
                        0.875),
                Arguments.of(
                        "script and glossary skipped, the rest full",
                        List.of(SCRIPT_SKIP, GLOSSARY_SKIP, LENGTH_PASS, ECHO_PASS, REPETITION_PASS),
                        1.0),
                Arguments.of(
                        "script and echo failed, the rest full",
                        List.of(SCRIPT_FAIL, ECHO_FAIL, GLOSSARY_PASS, LENGTH_PASS, REPETITION_PASS),
                        0.65));
    }

    @Test
    void blend_shuffledResultOrder_matchesFixedOrderValueExactly() {
        final List<CheckResult> shuffledOrder =
                List.of(REPETITION_PASS, ECHO_FAIL, SCRIPT_PASS, LENGTH_PASS, GLOSSARY_PASS);
        final List<CheckResult> fixedOrder =
                List.of(GLOSSARY_PASS, LENGTH_PASS, SCRIPT_PASS, ECHO_FAIL, REPETITION_PASS);

        final double shuffled = Confidence.blend(shuffledOrder);
        final double fixed = Confidence.blend(fixedOrder);

        assertThat(shuffled).isCloseTo(0.85, within(1e-9));
        assertThat(shuffled).isEqualTo(fixed);
    }
}
