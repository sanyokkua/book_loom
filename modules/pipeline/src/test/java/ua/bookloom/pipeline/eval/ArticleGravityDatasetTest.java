package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;

/**
 * The structure of the {@code article-gravity} reference book (redesign task 1.11): all twelve files open through the
 * real document port with the segment counts recorded here, a no-op write reopens with the same segments, and the
 * spans a translation must keep (URLs, DOIs, code, the foreign quotations, the local image) are identical in the four
 * languages. The remote image stays a reference in every format: nothing is fetched.
 */
class ArticleGravityDatasetTest {

    private static final DocumentPort DOCUMENTS =
            Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    private static final String REMOTE_IMAGE = "https://example.org/images/torsion-balance-schematic.svg";
    private static final String LATIN = "Hypotheses non fingo";
    private static final String GERMAN = "Die Waage lügt nie; wir sind nur zu ungeduldig, um ihr zuzuhören.";
    private static final String UKRAINIAN = "Тут ніщо не падає — усе просто летить разом зі мною.";
    private static final String FRENCH = "La pomme ne tombe pas : elle suit simplement le chemin le plus droit.";

    @TempDir
    private Path workDir;

    private static List<Segment> segments(final Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    // One format gives the same count in all four languages. The formats differ from each other because each holds the
    // article its own way: the EPUB adds 12 navigation labels, 12 chapter titles and 3 metadata segments; the FB2 has
    // no lists or code blocks, so every item and every code line is a paragraph (148), and its headings count 16.
    @ParameterizedTest(name = "{0}/gravity.{1}")
    @CsvSource({
        "en,md,152", "uk,md,152", "ru,md,152", "fr,md,152",
        "en,epub,169", "uk,epub,169", "ru,epub,169", "fr,epub,169",
        "en,fb2,207", "uk,fb2,207", "ru,fb2,207", "fr,fb2,207"
    })
    void open_everyFile_yieldsTheRecordedSegmentCount(final String language, final String extension, final int count) {
        final Document document = ExportedBooks.open(DOCUMENTS, ArticleGravity.file(language, extension));

        assertThat(segments(document)).hasSize(count);
    }

    @ParameterizedTest(name = "{0}/gravity.{1}")
    @CsvSource({
        "en,md", "uk,md", "ru,md", "fr,md",
        "en,epub", "uk,epub", "ru,epub", "fr,epub",
        "en,fb2", "uk,fb2", "ru,fb2", "fr,fb2"
    })
    void write_noOpReassembly_reopensWithTheSameSegmentSources(final String language, final String extension) {
        final Document original = ExportedBooks.open(DOCUMENTS, ArticleGravity.file(language, extension));
        final Path copy = workDir.resolve("copy." + extension);

        assertThat(DOCUMENTS.write(original, copy, language).isOk()).isTrue();
        final Document reopened = ExportedBooks.open(DOCUMENTS, copy);

        assertThat(segments(reopened).stream().map(Segment::sourceInner).toList())
                .isEqualTo(segments(original).stream().map(Segment::sourceInner).toList());
    }

    @Test
    void markdown_english_holdsTheFiveUrlsAndTwoDoisAndThirteenCodeIdentifiers() {
        final String english = ArticleGravity.markdown(ArticleGravity.SOURCE);

        assertThat(ArticleGravity.urls(english))
                .containsExactly(
                        REMOTE_IMAGE,
                        "https://doi.org/10.5555/rstl.1798.0022",
                        "https://doi.org/10.5555/RevModPhys.93.025010",
                        "https://example.org/nasa-science/earth/",
                        "https://www.esa.int/Science_Exploration");
        assertThat(ArticleGravity.dois(english))
                .containsExactly("10.5555/rstl.1798.0022", "10.5555/RevModPhys.93.025010");
        assertThat(ArticleGravity.codeIdentifiers(english))
                .containsExactly(
                        "base_station",
                        "st_001",
                        "st_002",
                        "station_id",
                        "latitude_deg",
                        "elevation_m",
                        "g_mGal",
                        "field_team_b",
                        "EARTH_MASS",
                        "EARTH_RADIUS",
                        "surface_gravity",
                        "mass_kg",
                        "radius_m");
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk", "ru", "fr"})
    void markdown_everyTranslation_keepsEveryProtectedSpanOfTheEnglish(final String language) {
        final String english = ArticleGravity.markdown(ArticleGravity.SOURCE);

        assertThat(ArticleGravity.markdown(language)).contains(ArticleGravity.protectedSpans(english));
        assertThat(ArticleGravity.keptShare(english, ArticleGravity.markdown(language)))
                .isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "ru", "fr"})
    void markdown_everyLanguage_hasTheSameFencedCodeLanguagesInOrder(final String language) {
        assertThat(ArticleGravity.fenceLanguages(ArticleGravity.markdown(language)))
                .containsExactly("text", "text", "mermaid", "text", "mermaid", "text", "json", "python", "bash");
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "en|" + LATIN,
                "en|" + GERMAN,
                "en|" + UKRAINIAN,
                "en|" + FRENCH,
                "uk|" + LATIN,
                "uk|" + GERMAN,
                "uk|" + FRENCH,
                "ru|" + LATIN,
                "ru|" + GERMAN,
                "ru|" + UKRAINIAN,
                "ru|" + FRENCH,
                "fr|" + LATIN,
                "fr|" + GERMAN,
                "fr|" + UKRAINIAN
            })
    void markdown_foreignQuotation_staysVerbatimInItsLanguage(final String language, final String quotation) {
        assertThat(ArticleGravity.markdown(language)).contains(quotation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "ru", "fr"})
    void markdown_everyLanguage_referencesTheLocalImageAndTheRemoteOne(final String language) {
        assertThat(ArticleGravity.markdown(language)).contains("](./Picture_of_earth.jpg)", "](" + REMOTE_IMAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "ru", "fr"})
    void epub_everyLanguage_embedsTheLocalImageAndKeepsTheRemoteOneAReference(final String language)
            throws IOException {
        final byte[] image =
                Files.readAllBytes(ArticleGravity.folder().resolve(language).resolve(ArticleGravity.IMAGE));
        try (ZipFile epub = new ZipFile(ArticleGravity.file(language, "epub").toFile())) {
            final ZipEntry embedded = epub.getEntry("OEBPS/images/" + ArticleGravity.IMAGE);
            final String chapters = epub.stream()
                    .filter(e -> e.getName().startsWith("OEBPS/text/"))
                    .map(e -> read(epub, e))
                    .reduce("", String::concat);

            assertThat(epub.getInputStream(embedded).readAllBytes()).isEqualTo(image);
            assertThat(epub.stream().map(ZipEntry::getName)).noneMatch(name -> name.endsWith(".svg"));
            assertThat(chapters).contains("<img src=\"" + REMOTE_IMAGE + "\"");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "ru", "fr"})
    void fb2_everyLanguage_embedsTheLocalImageAndLinksTheRemoteOne(final String language) throws IOException {
        final String fb2 = Files.readString(ArticleGravity.file(language, "fb2"), StandardCharsets.UTF_8);

        assertThat(fb2)
                .contains(
                        "<binary id=\"Picture_of_earth.jpg\" content-type=\"image/jpeg\">",
                        "l:href=\"#Picture_of_earth.jpg\"",
                        "l:href=\"" + REMOTE_IMAGE + "\"");
    }

    private static String read(final ZipFile zip, final ZipEntry entry) {
        try {
            return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
