package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.glossary.CsvRecords;

/**
 * The recurring terms as a plain file (15i.N7): RFC 4180 CSV in UTF-8 with the header {@code term,target}, the same
 * tokenizer as the glossary's. An import adds terms and fills a rendering a term does not have yet; it never replaces
 * a rendering the person typed or imported, replaces one a reading wrote (the file is the person's word, and a person's
 * rendering outranks a model's), and never lets a term into the lexicon that the glossary holds.
 */
class LexiconCsvTest {

    private static final String PROJECT = "p1";
    private static final String HEADER = "term,target";

    @TempDir
    Path dir;

    private LexiconServiceImpl service;
    private LexiconRepository lexicon;
    private GlossaryRepository glossary;

    private static Injector injector() {
        return Guice.createInjector(
                new DocumentModule(), new PersistenceModule(), new PipelineModule(), new ReviewModeTestModule());
    }

    @BeforeEach
    void setUp() {
        final Injector injector = injector();
        service = injector.getInstance(LexiconServiceImpl.class);
        lexicon = injector.getInstance(LexiconRepository.class);
        glossary = injector.getInstance(GlossaryRepository.class);
    }

    private Path write(final String... lines) throws IOException {
        final Path file = dir.resolve("terms.csv");
        Files.writeString(file, String.join("\r\n", lines) + "\r\n");
        return file;
    }

    // The header, then one record per term in the lexicon's own order, the target being the established rendering
    // (chosen, else learned, else the most used verified one, else the suggestion) or empty; a field is quoted only
    // when it holds a comma, a quote or a line break.
    @Test
    void exportCsv_heldTerms_writesTheHeaderThenEachTermWithItsEstablishedRendering() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "master").withChosen("пан"));
        lexicon.put(LexiconEntry.of(PROJECT, "imp"));
        lexicon.put(LexiconEntry.of(PROJECT, "sea, king").withChosen("морський король"));
        lexicon.put(LexiconEntry.of(PROJECT, "pasha").seen("паша"));
        final Path file = dir.resolve("terms.csv");

        final Result<Path> written = service.exportCsv(PROJECT, file);

        assertThat(written.data()).isEqualTo(file);
        assertThat(Files.readString(file))
                .isEqualTo(HEADER + "\r\nimp,\r\nmaster,пан\r\npasha,паша\r\n\"sea, king\",морський король\r\n");
    }

    @Test
    void exportCsv_emptyLexicon_writesTheHeaderOnly() throws IOException {
        final Path file = dir.resolve("terms.csv");

        service.exportCsv(PROJECT, file);

        assertThat(Files.readString(file)).isEqualTo(HEADER + "\r\n");
    }

    @Test
    void exportCsv_unwritableDestination_answersInternal() {
        final Result<Path> failed =
                service.exportCsv(PROJECT, dir.resolve("missing").resolve("terms.csv"));

        assertThat(failed.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
    }

    @Test
    void importCsv_newTerms_areAddedWithTheirRenderingAsChosenAndOneWithNoTargetWithNone() throws IOException {
        final Path file = write(HEADER, "master,пан", "imp,");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(2, List.of(), List.of()));
        assertThat(lexicon.all(PROJECT).data())
                .extracting(LexiconEntry::term, LexiconEntry::chosen)
                .containsExactly(tuple("imp", null), tuple("master", "пан"));
    }

    @Test
    void importCsv_fileWithNoHeader_readsItsFirstLineAsARow() throws IOException {
        final Path file = write("master,пан", "imp");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(2), List.of()));
        assertThat(lexicon.all(PROJECT).data()).extracting(LexiconEntry::term).containsExactly("master");
    }

    @Test
    void importCsv_headerSpelledWithCapitals_isSkipped() throws IOException {
        final Path file = write("Term,Target", "master,пан");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of()));
    }

    // A term the lexicon holds with only what drafts verified takes the file's rendering: the file outranks a count.
    @Test
    void importCsv_heldTermWithNoChosenRendering_takesTheRowsRendering() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "master").seen("господар"));
        final Path file = write(HEADER, "master,пан");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of()));
        assertThat(lexicon.all(PROJECT).data()).singleElement().satisfies(entry -> {
            assertThat(entry.chosen()).isEqualTo("пан");
            assertThat(entry.renderings())
                    .extracting(LexiconEntry.Rendering::text)
                    .containsExactly("господар");
        });
    }

    // IF an import replaced a chosen rendering, THEN opening a series file would undo the person's own wording.
    @Test
    void importCsv_heldTermWithAChosenRendering_isNeverReplaced() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "master").withChosen("пан"));
        final Path file = write(HEADER, "master,володар");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(0, List.of(), List.of()));
        assertThat(lexicon.all(PROJECT).data()).extracting(LexiconEntry::chosen).containsExactly("пан");
    }

    // A rendering a reading wrote is the model's hint: the file is the person's word and replaces it, and from then on
    // it is the person's, so a second import cannot replace it.
    @Test
    void importCsv_heldTermWithAModelsRendering_takesTheRowsRenderingAsThePersons() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "master").withModelRendering("господар"));
        final Path file = write(HEADER, "master,пан");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of()));
        assertThat(lexicon.all(PROJECT).data()).singleElement().satisfies(entry -> {
            assertThat(entry.chosen()).isEqualTo("пан");
            assertThat(entry.hasPersonsRendering()).isTrue();
        });
        service.importCsv(PROJECT, write(HEADER, "master,володар"));
        assertThat(lexicon.all(PROJECT).data()).extracting(LexiconEntry::chosen).containsExactly("пан");
    }

    @Test
    void importCsv_newTermWithATarget_isThePersonsRendering() throws IOException {
        service.importCsv(PROJECT, write(HEADER, "master,пан"));

        assertThat(lexicon.all(PROJECT).data())
                .singleElement()
                .satisfies(entry -> assertThat(entry.hasPersonsRendering()).isTrue());
    }

    @Test
    void importCsv_rowWithNoTargetForAHeldTerm_changesNothing() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "master").seen("господар"));
        final Path file = write(HEADER, "master,");

        service.importCsv(PROJECT, file);

        assertThat(lexicon.all(PROJECT).data())
                .singleElement()
                .satisfies(entry -> assertThat(entry.chosen()).isNull());
    }

    @Test
    void importCsv_termHeldIgnoringCase_fillsTheHeldEntryAndKeepsItsSpelling() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "Master"));
        final Path file = write(HEADER, "master,пан");

        service.importCsv(PROJECT, file);

        assertThat(lexicon.all(PROJECT).data())
                .extracting(LexiconEntry::term, LexiconEntry::chosen)
                .containsExactly(tuple("Master", "пан"));
    }

    // A term the glossary holds is the glossary's: the lexicon never gives a draft two renderings of one term.
    @Test
    void importCsv_termTheGlossaryHolds_isRefusedAndNotAdded() throws IOException {
        glossary.add(new GlossaryEntry("p1:pasha", PROJECT, "Pasha", "Паша", TermType.TERM, Gender.UNKNOWN, false));
        final Path file = write(HEADER, "master,пан", "pasha,паша");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of(3)));
        assertThat(lexicon.all(PROJECT).data()).extracting(LexiconEntry::term).containsExactly("master");
    }

    @ParameterizedTest
    @ValueSource(strings = {"master,пан,extra", "master", ",пан", "\"master,пан"})
    void importCsv_rowWithTheWrongFieldCountNoTermOrAnOpenQuote_isMalformed(final String row) throws IOException {
        final Path file = write(HEADER, row);

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(0, List.of(2), List.of()));
        assertThat(lexicon.all(PROJECT).data()).isEmpty();
    }

    @Test
    void importCsv_byteOrderMarkBlankLineAndNoFinalNewline_isReadAsUsual() throws IOException {
        final Path file = dir.resolve("terms.csv");
        Files.writeString(file, "\uFEFF" + HEADER + "\r\n\r\nmaster,пан\r\nimp,чорт");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(2, List.of(), List.of()));
    }

    @Test
    void importCsv_missingFile_answersValidation() {
        final Result<GlossaryImportReport> failed = service.importCsv(PROJECT, dir.resolve("absent.csv"));

        assertThat(failed.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }

    // A series keeps its terms across books: what one export wrote an import reads back into an empty lexicon.
    @Test
    void importCsv_exportedLexicon_importsBackIntoAnEmptyOneWithTheSameRenderings() throws IOException {
        lexicon.put(LexiconEntry.of(PROJECT, "master").withChosen("пан"));
        lexicon.put(LexiconEntry.of(PROJECT, "imp"));
        lexicon.put(LexiconEntry.of(PROJECT, "sea, king").withChosen("морський \"король\""));
        final Path file = dir.resolve("terms.csv");
        service.exportCsv(PROJECT, file);
        final Injector other = injector();

        final Result<GlossaryImportReport> report =
                other.getInstance(LexiconServiceImpl.class).importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(3, List.of(), List.of()));
        assertThat(other.getInstance(LexiconRepository.class).all(PROJECT).data())
                .extracting(LexiconEntry::term, LexiconEntry::chosen)
                .containsExactly(tuple("imp", null), tuple("master", "пан"), tuple("sea, king", "морський \"король\""));
    }

    // 15i.N7 makes the tokenizer public so the lexicon's file is read by the glossary's reader, not a second one.
    @Test
    void csvRecords_read_isUsableFromAnotherPackageAndKeepsQuotedFieldsAndLines() {
        final List<CsvRecords.CsvRecord> records = CsvRecords.read("term,target\r\n\"sea, king\",\"a\nb\"\r\nimp,\r\n");

        assertThat(records).extracting(CsvRecords.CsvRecord::line).containsExactly(1, 2, 4);
        assertThat(records.get(1).fields()).containsExactly("sea, king", "a\nb");
        assertThat(records.get(2).fields()).containsExactly("imp", "");
        assertThat(records.get(1).isBlankLine()).isFalse();
    }
}
