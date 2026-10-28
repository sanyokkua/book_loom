package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The committed baselines ({@code eval/baseline/*.json}, written by {@code eval-score.py baseline}) are the measure every
 * later group is compared with, so each must say which code produced it and which behaviour it stands for.
 */
class BaselineFilesTest {

    /** The commit whose behaviour the baselines record, in full. */
    private static final String BASELINE_OF = "79ffede6b2a550b66dc0c4ba95e6abed75031b96";

    private static final String SHORT_ID = BASELINE_OF.substring(0, 8);

    static Stream<Path> baselineFiles() throws IOException, URISyntaxException {
        final var folder = BaselineFilesTest.class.getResource("/eval/baseline");
        if (folder == null) {
            return Stream.empty();
        }
        try (Stream<Path> files = Files.list(Path.of(folder.toURI()))) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("baselineFiles")
    void baseline_everyFile_recordsItsCommitAndTodaysBehaviourAndHoldsOnlyInventedDatasets(final Path file)
            throws IOException {
        final JsonNode provenance = new ObjectMapper().readTree(file.toFile()).get("provenance");

        assertThat(Objects.requireNonNull(provenance).get("pipelineCommit").asText())
                .matches("[0-9a-f]{40}");
        assertThat(BASELINE_OF).startsWith(provenance.get("baselineOf").asText());
        assertThat(provenance.get("baselineOf").asText()).hasSizeGreaterThanOrEqualTo(SHORT_ID.length());
        assertThat(provenance.at("/dataset/ownerData").isBoolean()).isTrue();
        assertThat(provenance.at("/dataset/ownerData").asBoolean()).isFalse();
    }
}
