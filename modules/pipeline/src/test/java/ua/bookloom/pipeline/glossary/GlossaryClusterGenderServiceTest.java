package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/**
 * A gender the person chooses for one spelling of a name is chosen for the person (15i.N2): the other members of the
 * cluster take it, as the person's, unless the person set theirs; an edit that is not a gender choice, a cleared
 * gender, and an ambiguous or non-character entry leave the others alone. No book is open in these tests, so the
 * cluster is read from the entries alone.
 */
class GlossaryClusterGenderServiceTest {

    @TempDir
    Path dir;

    private GlossaryService service;

    @BeforeEach
    void setUp() {
        service = Guice.createInjector(
                        new DocumentModule(),
                        new InMemoryPersistenceModule(),
                        binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()))
                .getInstance(GlossaryServiceImpl.class);
    }

    // IF the edit reached only the entry the person touched, THEN the short name would be a man and the full name a
    // woman until the next review, and the sheet would say both.
    @Test
    void update_personChoosesTheShortNamesGender_everyOtherMemberTakesItAsThePersons() {
        service.add(character("Arlo"));
        service.add(character("Arlo Venn"));
        service.add(character("Venn").withSuggestedGender(Gender.FEMALE));

        final var stored = service.update(character("Arlo").withGender(Gender.MALE));

        assertThat(stored.data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender)
                .containsExactly("Arlo", Gender.MALE);
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Arlo Venn", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Venn", Gender.MALE, GenderOrigin.PERSON));
    }

    @Test
    void update_personChoosesTheFullNamesGender_theShortNamesTakeIt() {
        service.add(character("Arlo"));
        service.add(character("Arlo Venn"));
        service.add(character("Venn"));

        service.update(character("Arlo Venn").withGender(Gender.FEMALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.FEMALE, GenderOrigin.PERSON),
                        tuple("Arlo Venn", Gender.FEMALE, GenderOrigin.PERSON),
                        tuple("Venn", Gender.FEMALE, GenderOrigin.PERSON));
    }

    // Choosing the gender the entry shows already (a suggestion the person confirms) is a choice too.
    @ParameterizedTest
    @EnumSource(
            value = GenderOrigin.class,
            names = {"SUGGESTED", "VERIFIED"})
    void update_personConfirmsTheShownGender_theOthersTakeItToo(final GenderOrigin was) {
        final GlossaryEntry shown = holding(was);
        service.add(shown);
        service.add(character("Arlo Venn"));

        service.update(shown.withGender(Gender.MALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Arlo Venn", Gender.MALE, GenderOrigin.PERSON));
    }

    // Nothing the person set is overwritten, not even by the person's later choice for the same person.
    @Test
    void update_otherMemberHoldsAPersonsGender_keepsIt() {
        service.add(character("Arlo"));
        service.add(character("Arlo Venn").withGender(Gender.FEMALE));

        service.update(character("Arlo").withGender(Gender.MALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Arlo Venn", Gender.FEMALE, GenderOrigin.PERSON));
    }

    // IF the surname took the edit, THEN choosing a gender for Arlo would also change Mira's surname-only mentions.
    @Test
    void update_ambiguousSurname_isNotAMemberOfEitherCluster() {
        service.add(character("Arlo"));
        service.add(character("Arlo Venn"));
        service.add(character("Mira Venn"));
        service.add(character("Venn"));

        service.update(character("Arlo").withGender(Gender.MALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender)
                .containsExactly(
                        tuple("Arlo", Gender.MALE),
                        tuple("Arlo Venn", Gender.MALE),
                        tuple("Mira Venn", Gender.UNKNOWN),
                        tuple("Venn", Gender.UNKNOWN));
    }

    @Test
    void update_placeSharingAWordWithTheCharacter_isNotAMember() {
        service.add(character("Venn"));
        service.add(
                new GlossaryEntry("p1:Venn Hall", PROJECT, "Venn Hall", null, TermType.PLACE, Gender.UNKNOWN, false));

        service.update(character("Venn").withGender(Gender.FEMALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender)
                .containsExactly(tuple("Venn", Gender.FEMALE), tuple("Venn Hall", Gender.UNKNOWN));
    }

    // A target edit is not a gender choice: the others keep what they hold.
    @Test
    void update_targetEditOnAPersonsGender_leavesTheOtherMembersAlone() {
        service.add(character("Arlo").withGender(Gender.MALE));
        service.add(character("Arlo Venn"));

        service.update(character("Arlo").withGender(Gender.MALE).withTarget("Арло"));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.MALE, GenderOrigin.PERSON), tuple("Arlo Venn", Gender.UNKNOWN, null));
    }

    // Clearing a gender says "I do not know", not "this person has no gender": the others keep theirs.
    @Test
    void update_personClearsTheGender_theOtherMembersKeepTheirs() {
        service.add(character("Arlo").withGender(Gender.MALE));
        service.add(character("Arlo Venn").withSuggestedGender(Gender.MALE));

        service.update(character("Arlo").withGender(Gender.UNKNOWN));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.UNKNOWN, null), tuple("Arlo Venn", Gender.MALE, GenderOrigin.SUGGESTED));
    }

    // A CSV row's gender is the person's, so it is shared like a gender chosen in the table.
    // IF the import only wrote the row's own entry, THEN a file that sets the short name would leave the full name
    // with a model's other gender.
    @Test
    void importCsv_rowWithAGender_everyOtherMemberTakesItAsThePersons() throws IOException {
        service.add(character("Arlo"));
        service.add(character("Arlo Venn").withSuggestedGender(Gender.FEMALE));
        final Path file = Files.writeString(
                dir.resolve("names.csv"), "term,target,type,gender,locked\nArlo,,character,male,false\n");

        service.importCsv(PROJECT, file);

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Arlo Venn", Gender.MALE, GenderOrigin.PERSON));
    }

    @Test
    void importCsv_newRowWithAGender_theHeldMembersTakeIt() throws IOException {
        service.add(character("Arlo Venn"));
        final Path file = Files.writeString(
                dir.resolve("names.csv"), "term,target,type,gender,locked\nArlo,,character,male,false\n");

        service.importCsv(PROJECT, file);

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo Venn", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Arlo", Gender.MALE, GenderOrigin.PERSON));
    }

    // Mrs Venn is often the wife of Arlo Venn: choosing a man's gender for him must not make her a man.
    @Test
    void update_personChoosesAMansGender_aMrsOfTheSameSurnameKeepsHers() {
        service.add(character("Arlo Venn"));
        service.add(character("Mrs Venn"));

        service.update(character("Arlo Venn").withGender(Gender.MALE));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender)
                .containsExactly(tuple("Arlo Venn", Gender.MALE), tuple("Mrs Venn", Gender.UNKNOWN));
    }

    private static GlossaryEntry holding(final GenderOrigin origin) {
        return switch (origin) {
            case SUGGESTED -> character("Arlo").withSuggestedGender(Gender.MALE);
            case VERIFIED -> character("Arlo").withInferredGender(Gender.MALE);
            case PERSON -> character("Arlo").withGender(Gender.MALE);
        };
    }

    private static GlossaryEntry character(final String term) {
        return new GlossaryEntry("p1:" + term, PROJECT, term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }
}
