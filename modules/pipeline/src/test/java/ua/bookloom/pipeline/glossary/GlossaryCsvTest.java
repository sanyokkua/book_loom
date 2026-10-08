package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;

/** The glossary file: what is written, what an import updates, adds, skips and reports. */
class GlossaryCsvTest {

    private static final String HEADER = "term,target,type,gender,locked";

    @TempDir
    Path dir;

    private GlossaryService service;

    @BeforeEach
    void setUp() {
        service = Guice.createInjector(
                        new DocumentModule(),
                        new PersistenceModule(),
                        binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()))
                .getInstance(GlossaryServiceImpl.class);
    }

    @Test
    void exportCsv_termWithComma_quotesTheFields() throws IOException {
        service.add(new GlossaryEntry(
                "p1:hale, margaret",
                PROJECT,
                "Hale, Margaret",
                "Гейл, Маргарет",
                TermType.CHARACTER,
                Gender.FEMALE,
                true));
        final Path file = dir.resolve("glossary.csv");

        final Result<Path> written = service.exportCsv(PROJECT, file);

        assertThat(written.data()).isEqualTo(file);
        assertThat(Files.readString(file))
                .isEqualTo(HEADER + "\r\n\"Hale, Margaret\",\"Гейл, Маргарет\",character,female,true\r\n");
    }

    @Test
    void exportCsv_emptyGlossary_writesTheHeaderOnly() throws IOException {
        final Path file = dir.resolve("glossary.csv");

        service.exportCsv(PROJECT, file);

        assertThat(Files.readString(file)).isEqualTo(HEADER + "\r\n");
    }

    @Test
    void exportCsv_unwritableDestination_answersInternal() {
        final Result<Path> failed =
                service.exportCsv(PROJECT, dir.resolve("missing").resolve("glossary.csv"));

        assertThat(failed.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
    }

    @Test
    void format_fieldWithQuoteAndLineBreak_isQuotedWithDoubledQuotes() {
        final GlossaryEntry entry =
                new GlossaryEntry("p1:x", PROJECT, "Say \"Hi\"\nnow", null, TermType.OTHER, Gender.UNKNOWN, false);

        assertThat(GlossaryCsv.format(List.of(entry)))
                .isEqualTo(HEADER + "\r\n\"Say \"\"Hi\"\"\nnow\",,other,unknown,false\r\n");
    }

    @Test
    void importCsv_lineThreeHasTwoFields_reportsItMalformedAndImportsTheRest() throws IOException {
        final Path file =
                write(HEADER, "Hale,Гейл,character,male,true", "Milton,Мілтон", "Moreau,Моро,character,male,false");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(2, List.of(3), List.of()));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term)
                .containsExactly("Hale", "Moreau");
    }

    @Test
    void importCsv_fileWithNoHeader_readsItsFirstLineAsARow() throws IOException {
        final Path file = write("Hale,Гейл,character,male,true", "Milton,Мілтон");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(2), List.of()));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term)
                .containsExactly("Hale");
    }

    @Test
    void importCsv_headerSpelledWithCapitals_isSkipped() throws IOException {
        final Path file = write("Term,Target,Type,Gender,Locked", "Hale,Гейл,character,male,true");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of()));
    }

    @Test
    void importCsv_lockedRowWithNoTarget_reportsItRefusedAndAddsNoEntry() throws IOException {
        final Path file = write(HEADER, "Hale,,character,male,true");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(0, List.of(), List.of(2)));
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    @Test
    void importCsv_termAlreadyHeldIgnoringCase_updatesItInPlace() throws IOException {
        service.add(new GlossaryEntry("p1:hale", PROJECT, "Hale", "Хейл", TermType.OTHER, Gender.UNKNOWN, false));
        final Path file = write(HEADER, "hale,Гейл,character,male,true");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of()));
        assertThat(service.entries(PROJECT).data())
                .containsExactly(
                        new GlossaryEntry("p1:hale", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));
    }

    @Test
    void importCsv_rescanRowTypedTermWithUnknownGender_keepsTheHeldCharacterAndGender() throws IOException {
        service.add(
                new GlossaryEntry("p1:chisel", PROJECT, "Chisel", "Чизел", TermType.CHARACTER, Gender.FEMALE, false));
        final Path file = write(HEADER, "Chisel,Чизел,term,unknown,false");

        service.importCsv(PROJECT, file);

        assertThat(service.entries(PROJECT).data())
                .containsExactly(new GlossaryEntry(
                        "p1:chisel", PROJECT, "Chisel", "Чизел", TermType.CHARACTER, Gender.FEMALE, false));
    }

    @Test
    void importCsv_rowWithAKnownGender_replacesTheHeldOne() throws IOException {
        service.add(
                new GlossaryEntry("p1:chisel", PROJECT, "Chisel", "Чизел", TermType.CHARACTER, Gender.FEMALE, false));
        final Path file = write(HEADER, "Chisel,Чизел,character,male,false");

        service.importCsv(PROJECT, file);

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender)
                .containsExactly(Gender.MALE);
    }

    @Test
    void importCsv_unlockedRowWithNoTarget_addsAnEntryWithNoTarget() throws IOException {
        final Path file = write(HEADER, "Milton,,place,unknown,false");

        service.importCsv(PROJECT, file);

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::type)
                .containsExactly(tuple("Milton", null, TermType.PLACE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Hale,Гейл,person,male,true",
                "Hale,Гейл,character,robot,true",
                "Hale,Гейл,character,male,yes",
                "Hale,Гейл,character,male,true,extra",
                ",Гейл,character,male,true"
            })
    void importCsv_rowWithAnUnknownValueOrWrongFieldCount_isMalformed(final String row) throws IOException {
        final Path file = write(HEADER, row);

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(0, List.of(2), List.of()));
    }

    @Test
    void importCsv_quotedFieldSpanningLines_reportsTheLineEachRecordStartsOn() throws IOException {
        final Path file = write(
                HEADER, "\"Hale\nSr\",Гейл,character,male,true", "Milton,Мілтон", "Moreau,Моро,character,male,false");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(2, List.of(4), List.of()));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term)
                .containsExactly("Hale\nSr", "Moreau");
    }

    @Test
    void importCsv_byteOrderMarkCrlfBlankLineAndNoFinalNewline_isReadAsUsual() throws IOException {
        final Path file = dir.resolve("glossary.csv");
        Files.writeString(
                file, "﻿" + HEADER + "\r\n\r\nHale,Гейл,character,male,true\r\nMoreau,Моро,character,male,false");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(2, List.of(), List.of()));
    }

    @Test
    void importCsv_quoteThatNeverCloses_isMalformed() throws IOException {
        final Path file = write(HEADER, "\"Hale,Гейл,character,male,true");

        assertThat(service.importCsv(PROJECT, file).data())
                .isEqualTo(new GlossaryImportReport(0, List.of(2), List.of()));
    }

    @Test
    void importCsv_missingFile_answersValidation() {
        final Result<GlossaryImportReport> failed = service.importCsv(PROJECT, dir.resolve("absent.csv"));

        assertThat(failed.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }

    @Test
    void importCsv_fileThatIsNotUtf8_answersValidation() throws IOException {
        final Path file = dir.resolve("glossary.csv");
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});

        assertThat(service.importCsv(PROJECT, file).error())
                .extracting(AppError::code)
                .isEqualTo(ErrorCode.validation);
    }

    @Test
    void importCsv_exportedGlossary_importsBackIntoAnEmptyOneUnchanged() throws IOException {
        final List<GlossaryEntry> entries = List.of(
                new GlossaryEntry(
                        "p1:hale, margaret",
                        PROJECT,
                        "Hale, Margaret",
                        "Гейл, Маргарет",
                        TermType.CHARACTER,
                        Gender.FEMALE,
                        true),
                new GlossaryEntry("p1:say", PROJECT, "Say \"Hi\"\nnow", "Скажи", TermType.TITLE, Gender.NEUTER, false),
                new GlossaryEntry("p1:milton", PROJECT, "Milton", null, TermType.PLACE, Gender.UNKNOWN, false));
        entries.forEach(service::add);
        final Path file = dir.resolve("glossary.csv");
        service.exportCsv(PROJECT, file);

        final Result<GlossaryImportReport> report = service.importCsv("p2", file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(3, List.of(), List.of()));
        assertThat(service.entries("p2").data())
                .extracting(
                        GlossaryEntry::term,
                        GlossaryEntry::target,
                        GlossaryEntry::type,
                        GlossaryEntry::gender,
                        GlossaryEntry::locked)
                .containsExactly(
                        tuple("Hale, Margaret", "Гейл, Маргарет", TermType.CHARACTER, Gender.FEMALE, true),
                        tuple("Say \"Hi\"\nnow", "Скажи", TermType.TITLE, Gender.NEUTER, false),
                        tuple("Milton", null, TermType.PLACE, Gender.UNKNOWN, false));
    }

    // A first run's good glossary is imported into the next book: a suggestion becomes the person's own choice there,
    // and
    // the lock and the gender travel as they were.
    @Test
    void importCsv_exportedSuggestionAndLockedNames_keepLockAndGenderAndTheSuggestionBecomesTheirs()
            throws IOException {
        service.add(new GlossaryEntry(
                "p1:bartimaeus",
                PROJECT,
                "Bartimaeus",
                "Бартімей",
                TermType.CHARACTER,
                Gender.MALE,
                false,
                TargetOrigin.SUGGESTED));
        service.add(new GlossaryEntry(
                "p1:nathaniel", PROJECT, "Nathaniel", "Натаніел", TermType.CHARACTER, Gender.MALE, true));
        final Path file = dir.resolve("glossary.csv");
        service.exportCsv(PROJECT, file);

        service.importCsv("p2", file);

        assertThat(service.entries("p2").data())
                .extracting(
                        GlossaryEntry::term,
                        GlossaryEntry::target,
                        GlossaryEntry::gender,
                        GlossaryEntry::locked,
                        GlossaryEntry::isSuggested)
                .containsExactly(
                        tuple("Bartimaeus", "Бартімей", Gender.MALE, false, false),
                        tuple("Nathaniel", "Натаніел", Gender.MALE, true, false));
    }

    private Path write(final String... lines) throws IOException {
        return Files.writeString(dir.resolve("glossary.csv"), String.join("\n", lines) + "\n");
    }
}
