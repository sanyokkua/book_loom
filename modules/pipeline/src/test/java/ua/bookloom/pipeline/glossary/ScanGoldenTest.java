package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Document;

/**
 * Characterisation of the six book readers (name scan, key-term scan, name windows, term evidence, unknown-gender
 * mentions, pronoun tallies) recorded as {@code golden/scans/<book>.json} before they were switched to the book index,
 * which must go on producing the very same files. To record the files again, run {@code BOOKLOOM_WRITE_GOLDENS=1
 * ./gradlew :pipeline:test --tests '*ScanGoldenTest.regenerate*'} — a change to a golden file is a change of behaviour
 * and needs its own reason.
 */
class ScanGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden/scans");

    static Stream<String> books() {
        return ScanGoldenBooks.ids();
    }

    // IF every reader reads the project's BookIndex instead of the book, THEN its answers are still the recorded ones.
    @ParameterizedTest
    @MethodSource("books")
    void scans_overBookIndex_equalTheGolden(final String book, @TempDir final Path dir) {
        final Document document = ScanGoldenBooks.open(book, dir);

        assertThat(pretty(ScanSnapshot.overIndex(document))).isEqualTo(pretty(golden(book)));
    }

    // IF a golden were empty, it would pin nothing, so every book must record names, windows, evidence and pronouns.
    @ParameterizedTest
    @MethodSource("books")
    void golden_everyBook_pinsNamesWindowsAndPronouns(final String book) {
        final JsonNode golden = golden(book);

        assertThat(golden.get("nameScan")).isNotEmpty();
        assertThat(golden.get("nameScanFirstHalf")).isNotNull();
        assertThat(golden.get("windows")).isNotEmpty();
        assertThat(golden.get("pronouns")).isNotEmpty();
        assertThat(golden.get("termEvidence")).isNotEmpty();
        assertThat(golden.get("unknownGenderMentions")).isNotEmpty();
        assertThat(golden.get("storySegments").asInt()).isPositive();
    }

    // The recorder: it writes the files from the index path and is skipped unless asked for.
    @ParameterizedTest
    @MethodSource("books")
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_WRITE_GOLDENS", matches = "1")
    void regenerate_envSet_writesTheGolden(final String book, @TempDir final Path dir) throws IOException {
        final Document document = ScanGoldenBooks.open(book, dir);

        Files.createDirectories(GOLDEN_DIR);
        Files.writeString(GOLDEN_DIR.resolve(book + ".json"), pretty(ScanSnapshot.overIndex(document)) + "\n");

        assertThat(GOLDEN_DIR.resolve(book + ".json")).exists();
    }

    private static JsonNode golden(final String book) {
        final String resource = "/golden/scans/" + book + ".json";
        try (InputStream in = ScanGoldenTest.class.getResourceAsStream(resource)) {
            return MAPPER.readTree(Objects.requireNonNull(in, resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String pretty(final Map<String, Object> snapshot) {
        final JsonNode tree = MAPPER.valueToTree(snapshot);
        return pretty(tree);
    }

    private static String pretty(final JsonNode tree) {
        try {
            return MAPPER.writeValueAsString(tree);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
