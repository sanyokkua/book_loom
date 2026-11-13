package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.typography.Normalisation;
import ua.bookloom.pipeline.typography.TypographyNormalizer;

/**
 * Runs the ru-uk detector cases. A finding is named by its kebab-case kind, so a case for a check that group 10 has
 * not built yet fails on the assertion, not at compile time. An {@code expected-miss} case records what only the
 * proofread can catch: no finding at all.
 */
class DetectorsRuUkTest {

    private static final String CASES = "/eval/datasets/detectors-ru-uk/cases.json";
    private static final List<String> REPAIRS =
            List.of("stress-mark", "leading-dash", "dialogue-dash", "heading-number");
    private static final Set<String> NO_FINDING = Set.of("none", "expected-miss");

    static Stream<JsonNode> cases() throws IOException {
        try (InputStream in = DetectorsRuUkTest.class.getResourceAsStream(CASES)) {
            assertThat(in).as(CASES).isNotNull();
            return Stream.of(new ObjectMapper().readTree(in)).flatMap(JsonNode::valueStream);
        }
    }

    static Stream<JsonNode> raisingCases() throws IOException {
        return cases().filter(c -> !NO_FINDING.contains(c.get("expect").asText()));
    }

    static Stream<JsonNode> silentCases() throws IOException {
        return cases().filter(c -> NO_FINDING.contains(c.get("expect").asText()));
    }

    @ParameterizedTest
    @MethodSource("raisingCases")
    void detector_labelledCase_raisesTheNamedFinding(final JsonNode detectorCase) {
        assertThat(raised(detectorCase))
                .as(
                        "%s: %s",
                        detectorCase.get("id").asText(),
                        detectorCase.get("note").asText())
                .contains(detectorCase.get("expect").asText());
    }

    @ParameterizedTest
    @MethodSource("silentCases")
    void detector_cleanOrExpectedMissCase_raisesNothing(final JsonNode detectorCase) {
        assertThat(raised(detectorCase))
                .as(
                        "%s: %s",
                        detectorCase.get("id").asText(),
                        detectorCase.get("note").asText())
                .isEmpty();
    }

    // The code repairs run before any check, so a defect they remove is "raised" by the repair that fires (10.3).
    private static List<String> raised(final JsonNode detectorCase) {
        final String source = detectorCase.get("source").asText();
        final String target = detectorCase.get("target").asText();
        final Normalisation normalised = TypographyNormalizer.normalise(source, target, "uk");
        final List<String> raised =
                new ArrayList<>(REPAIRS.stream().filter(normalised::fired).toList());
        TextChecks.run(source, normalised.text(), "ru", "uk", List.of(), List.of()).stream()
                .map(finding -> finding.kind().name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .forEach(raised::add);
        return raised;
    }
}
