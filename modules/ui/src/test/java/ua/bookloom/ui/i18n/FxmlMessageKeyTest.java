package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every {@code %key} an FXML file asks the loader to resolve is a real {@link MessageKey} defined in both
 * catalogues. The loader reports a missing key only when that file is loaded, so a misspelling in a screen nobody has
 * opened would otherwise ship; this test is in force from the first FXML file.
 */
class FxmlMessageKeyTest {

    private static final Pattern KEY_REFERENCE = Pattern.compile("\"%([^\"%]+)\"");
    private static final String THEME_STYLESHEET = "/ua/bookloom/ui/theme.css";
    private static final List<String> PLACEHOLDERS = List.of(
            "import.fxml", "book-brief.fxml", "structure.fxml", "translating.fxml", "export.fxml", "settings.fxml");

    private static List<String> referencesIn(final String fxml) {
        final Matcher matcher = KEY_REFERENCE.matcher(fxml);
        return matcher.results().map(match -> match.group(1)).toList();
    }

    private static List<String> unknownAmong(final List<String> references) {
        final List<String> registered =
                Arrays.stream(MessageKey.values()).map(MessageKey::key).toList();
        return references.stream()
                .filter(reference -> !registered.contains(reference))
                .toList();
    }

    // Located from theme.css, which sits at the root of the module's own resources, so the scan cannot silently
    // look somewhere that holds no screens.
    private static List<Path> mainFxmlFiles() throws IOException, URISyntaxException {
        final URL stylesheet = FxmlMessageKeyTest.class.getResource(THEME_STYLESHEET);
        assertThat(stylesheet).as("theme.css locates the main resource root").isNotNull();
        final Path resourceRoot = Path.of(stylesheet.toURI()).getParent();
        try (Stream<Path> walk = Files.walk(resourceRoot)) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".fxml"))
                    .sorted()
                    .toList();
        }
    }

    static Stream<Path> fxmlFiles() throws IOException, URISyntaxException {
        return mainFxmlFiles().stream();
    }

    // IF the scan found nothing (a wrong root), THEN every case below would pass vacuously; this names the six
    // placeholders so an empty scan fails.
    @Test
    void scan_mainResources_findsEverySixPlaceholder() throws Exception {
        final List<String> names = mainFxmlFiles().stream()
                .map(path -> path.getFileName().toString())
                .toList();

        assertThat(names).hasSizeGreaterThanOrEqualTo(6).containsAll(PLACEHOLDERS);
    }

    // IF a file referenced a key that is not a MessageKey, or one missing from a catalogue, THEN the loader would fail
    // (or show a raw key) only when that screen was first opened; this fails at build time and names the file.
    @ParameterizedTest
    @MethodSource("fxmlFiles")
    void fxml_everyKeyReference_isAMessageKeyDefinedInBothCatalogues(final Path file) throws IOException {
        final List<String> references = referencesIn(Files.readString(file));

        assertThat(unknownAmong(references))
                .as("keys in %s that are not a MessageKey", file)
                .isEmpty();
        assertThat(Catalogues.load("en").keySet())
                .as("English catalogue, %s", file)
                .containsAll(references);
        assertThat(Catalogues.load("uk").keySet())
                .as("Ukrainian catalogue, %s", file)
                .containsAll(references);
    }

    // Negative control: the detector must be able to fail, or the empty results above mean nothing.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "'<Label text=\"%nav.imprt\"/>' | nav.imprt",
                "'<Label text=\"%nav.import\"/><Button text=\"%nav.expot\"/>' | nav.expot",
            })
    void unknownAmong_misspeltReference_isReported(final String fxml, final String expectedUnknown) {
        assertThat(unknownAmong(referencesIn(fxml))).containsExactly(expectedUnknown);
    }

    @Test
    void referencesIn_severalAttributes_findsEachReferenceOnce() {
        // IF only the text attribute were scanned, THEN a promptText or tooltip typo would slip through.
        final String fxml = "<TextField promptText=\"%nav.import\"/><Label text=\"%nav.export\" id=\"plain\"/>";

        assertThat(referencesIn(fxml)).containsExactly("nav.import", "nav.export");
    }

    @Test
    void unknownAmong_validReferences_isEmpty() {
        // IF a real key were reported unknown, THEN every placeholder would fail for the wrong reason.
        assertThat(unknownAmong(List.of("nav.import", "nav.brief", "nav.namesAndStyle")))
                .isEmpty();
    }
}
