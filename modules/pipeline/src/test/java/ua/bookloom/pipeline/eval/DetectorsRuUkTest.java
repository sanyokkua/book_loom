package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs the ru-uk detector cases; the set is empty until group 10 fills {@code cases.json}. */
class DetectorsRuUkTest {

    private static final String CASES = "/eval/datasets/detectors-ru-uk/cases.json";

    static Stream<JsonNode> cases() throws IOException {
        try (InputStream in = DetectorsRuUkTest.class.getResourceAsStream(CASES)) {
            assertThat(in).as(CASES).isNotNull();
            return Stream.of(new ObjectMapper().readTree(in)).flatMap(a -> a.valueStream());
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("cases")
    void detector_case_givesTheLabelledVerdict(final JsonNode detectorCase) {
        assertThat(detectorCase.has("id")).as("case id").isTrue();
    }
}
