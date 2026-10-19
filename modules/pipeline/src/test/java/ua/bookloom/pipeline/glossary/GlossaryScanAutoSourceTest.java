package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.repeated;

import com.google.inject.Guice;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/** The deterministic glossary scan of a book whose source language is Auto and which declares none. */
class GlossaryScanAutoSourceTest {

    private GlossaryService service;
    private OpenProjects openProjects;

    @BeforeEach
    void setUp() {
        final var injector = Guice.createInjector(
                new DocumentModule(),
                new PersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
        openProjects = injector.getInstance(OpenProjects.class);
    }

    private static Document declaringNothing(final List<String> body) {
        final Document declared = book(body, "Title");
        return new Document(
                declared.id(),
                declared.format(),
                null,
                null,
                null,
                null,
                declared.contentHash(),
                declared.metadata(),
                declared.units());
    }

    private List<GlossaryEntry> scanned() {
        return Objects.requireNonNull(service.scan(PROJECT).data(), "scan").added();
    }

    // IF "Le" were read as an English word, THEN a French book's glossary would open with the name "Le Rouge".
    @Test
    void scan_frenchBookDeclaringNothing_neverProposesAFunctionWordAsAName() {
        openProjects.put(PROJECT, declaringNothing(repeated("Il lisait Le Rouge hier soir.", 4)));

        assertThat(scanned()).extracting(GlossaryEntry::term).containsExactly("Rouge");
    }

    // IF English pronouns were read in a book of unknown language, THEN the glossary would suggest a gender as proof.
    @Test
    void scan_bookDeclaringNothing_suggestsNoGenderFromPronouns() {
        openProjects.put(PROJECT, declaringNothing(repeated("We saw Hale there. She smiled.", 4)));

        final List<GlossaryEntry> added = scanned();

        assertThat(added).extracting(GlossaryEntry::term).containsExactly("Hale");
        assertThat(added.getFirst().gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(added.getFirst().genderOrigin()).isNull();
    }

    // IF a first-name list were chosen for an unknown language, THEN a name would be gendered by a list for no
    // language.
    @Test
    void scan_bookDeclaringNothing_suggestsNoGenderFromAFirstName() {
        openProjects.put(PROJECT, declaringNothing(repeated("We saw Rosalind there.", 4)));

        final List<GlossaryEntry> added = scanned();

        assertThat(added).extracting(GlossaryEntry::term).containsExactly("Rosalind");
        assertThat(added.getFirst().genderOrigin()).isNull();
    }

    // A book that does declare a language the application recognises is still analysed in it under Auto.
    @Test
    void scan_bookDeclaringEnglish_stillReadsItsPronouns() {
        openProjects.put(PROJECT, book(repeated("We saw Hale there. She smiled.", 4), "Title"));

        final List<GlossaryEntry> added = scanned();

        assertThat(added.getFirst().gender()).isEqualTo(Gender.FEMALE);
    }
}
