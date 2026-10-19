package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;

/**
 * The glossary port keeps a gender's origin (15i.N0): the dialog, the table edit and a CSV row with a gender write the
 * person's; an edit of anything else leaves the origin alone; a CSV row without a gender never turns a known gender
 * into an unknown one or changes who it came from. The file has no origin column, so a re-import is the person's.
 */
class GlossaryGenderOriginServiceTest {

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

    // IF the Add dialog's entry carried no origin for a chosen gender, THEN the next review could overwrite what the
    // person typed.
    @Test
    void add_dialogEntryWithAChosenGender_storesThePersonsOrigin() {
        service.add(
                new GlossaryEntry("p1:justine", PROJECT, "Justine", null, TermType.CHARACTER, Gender.FEMALE, false));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.FEMALE, GenderOrigin.PERSON));
    }

    @Test
    void add_dialogEntryWithNoGender_storesNoOrigin() {
        service.add(
                new GlossaryEntry("p1:justine", PROJECT, "Justine", null, TermType.CHARACTER, Gender.UNKNOWN, false));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.UNKNOWN, null));
    }

    @ParameterizedTest
    @EnumSource(GenderOrigin.class)
    void add_entryWithAnyOrigin_storesItAsGiven(final GenderOrigin origin) {
        service.add(holding(origin, Gender.MALE, "Wren"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::genderOrigin)
                .containsExactly(origin);
    }

    // The table's gender combo (and choosing the shown value again) goes through the entry's own withGender.
    @ParameterizedTest
    @EnumSource(GenderOrigin.class)
    void update_genderChosenByThePerson_storesThePersonsOrigin(final GenderOrigin was) {
        final GlossaryEntry held = holding(was, Gender.MALE, "Wren");
        service.add(held);

        service.update(held.withGender(Gender.MALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.MALE, GenderOrigin.PERSON));
    }

    // IF the service rewrote the origin of every entry it stores, THEN a target edit would turn a verified gender into
    // the person's, which no later verdict could correct.
    @ParameterizedTest
    @EnumSource(GenderOrigin.class)
    void update_targetEdit_keepsTheGendersOrigin(final GenderOrigin origin) {
        final GlossaryEntry held = holding(origin, Gender.MALE, "Wren");
        service.add(held);

        service.update(held.withTarget("Рен"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::genderOrigin)
                .containsExactly(tuple("Рен", origin));
    }

    @Test
    void update_genderClearedToUnknown_storesNoOrigin() {
        final GlossaryEntry held = holding(GenderOrigin.PERSON, Gender.MALE, "Wren");
        service.add(held);

        service.update(held.withGender(Gender.UNKNOWN));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.UNKNOWN, null));
    }

    @ParameterizedTest
    @CsvSource({"male,MALE", "female,FEMALE", "neuter,NEUTER"})
    void importCsv_newRowWithAGender_storesThePersonsOrigin(final String column, final Gender expected)
            throws IOException {
        service.importCsv(PROJECT, write(HEADER, "Wren,,character," + column + ",false"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(expected, GenderOrigin.PERSON));
    }

    @Test
    void importCsv_newRowWithUnknownGender_storesNoOrigin() throws IOException {
        service.importCsv(PROJECT, write(HEADER, "Wren,,character,unknown,false"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.UNKNOWN, null));
    }

    // The file is the person's word: a row naming a gender replaces a suggested or verified one, even the same value.
    @ParameterizedTest
    @CsvSource({
        "SUGGESTED, male, MALE",
        "SUGGESTED, female, FEMALE",
        "VERIFIED, male, MALE",
        "VERIFIED, female, FEMALE",
        "PERSON, male, MALE",
        "PERSON, female, FEMALE"
    })
    void importCsv_rowWithAGender_overAHeldGender_isThePersons(
            final GenderOrigin was, final String column, final Gender expected) throws IOException {
        service.add(holding(was, Gender.MALE, "Wren"));

        service.importCsv(PROJECT, write(HEADER, "Wren,,character," + column + ",false"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(expected, GenderOrigin.PERSON));
    }

    // 15h.B6: a rescan's file lists a character as "unknown" before anybody settled it; importing that must not undo
    // the gender, nor change who settled it (a suggestion must not silently become the person's).
    @ParameterizedTest
    @EnumSource(GenderOrigin.class)
    void importCsv_rowWithUnknownGender_keepsTheHeldGenderAndItsOrigin(final GenderOrigin was) throws IOException {
        service.add(holding(was, Gender.FEMALE, "Wren"));

        service.importCsv(PROJECT, write(HEADER, "Wren,Рен,term,unknown,false"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(TermType.CHARACTER, Gender.FEMALE, was));
    }

    // IF an import dropped the "first-name list already tried" flag, THEN a gender the person reset to unknown would be
    // suggested again at the next run start.
    @Test
    void importCsv_rowWithUnknownGender_overAGenderThePersonResetKeepsTheTriedFlag() throws IOException {
        service.add(holding(GenderOrigin.PERSON, Gender.MALE, "Wren").withGender(Gender.UNKNOWN));

        service.importCsv(PROJECT, write(HEADER, "Wren,,character,unknown,false"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin, GlossaryEntry::genderSeedTried)
                .containsExactly(tuple(Gender.UNKNOWN, null, true));
    }

    // The file keeps today's five columns and no origin: the origin of an export is lost, and every gender read back
    // is the person's.
    @Test
    void exportCsv_entriesOfEveryOrigin_writeTheFiveColumnsOnly() throws IOException {
        addOneOfEachOrigin();
        final Path file = dir.resolve("out.csv");

        service.exportCsv(PROJECT, file);

        assertThat(Files.readString(file))
                .isEqualTo(HEADER + "\r\n"
                        + "Hale,,character,male,false\r\n"
                        + "Rook,,character,female,false\r\n"
                        + "Wren,,character,male,false\r\n"
                        + "Moss,,character,unknown,false\r\n");
    }

    @Test
    void importCsv_exportOfEveryOrigin_keepsEachGenderAsThePersonsAndTheUnknownOneWithoutOrigin() throws IOException {
        addOneOfEachOrigin();
        final Path file = dir.resolve("out.csv");
        service.exportCsv(PROJECT, file);

        service.importCsv("p2", file);

        assertThat(service.entries("p2").data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Hale", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Rook", Gender.FEMALE, GenderOrigin.PERSON),
                        tuple("Wren", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Moss", Gender.UNKNOWN, null));
    }

    private void addOneOfEachOrigin() {
        service.add(holding(GenderOrigin.SUGGESTED, Gender.MALE, "Hale"));
        service.add(holding(GenderOrigin.VERIFIED, Gender.FEMALE, "Rook"));
        service.add(holding(GenderOrigin.PERSON, Gender.MALE, "Wren"));
        service.add(holding(null, Gender.UNKNOWN, "Moss"));
    }

    // The entry as each writer leaves it: the person's edit, a verified answer, a suggestion, or nothing known.
    private static GlossaryEntry holding(@Nullable final GenderOrigin origin, final Gender gender, final String term) {
        final GlossaryEntry unknown = new GlossaryEntry(
                GlossaryIds.of(PROJECT, term), PROJECT, term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
        if (origin == null) {
            return unknown;
        }
        return switch (origin) {
            case PERSON -> unknown.withGender(gender);
            case VERIFIED -> unknown.withInferredGender(gender);
            case SUGGESTED -> unknown.withSuggestedGender(gender);
        };
    }

    private Path write(final String... lines) throws IOException {
        return Files.writeString(dir.resolve("glossary.csv"), String.join("\n", lines) + "\n");
    }
}
