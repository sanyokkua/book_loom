package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.repeated;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The services that read the whole book do so through the project's book index: a book put in {@link OpenProjects} has
 * no index until a service needs one, and once a scan or a count has run, the index is there for every later reader.
 * The answers stay what they were.
 */
class ProjectIndexReadsTest {

    private GlossaryService service;
    private OpenProjects openProjects;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
        openProjects = injector.getInstance(OpenProjects.class);
    }

    // IF Start counts the mentions of unknown-gender characters, THEN it asks the project's index, which it builds
    // once.
    @Test
    void unknownGenders_openBook_readsTheProjectsIndex() {
        openProjects.put(
                PROJECT,
                book(
                        List.of(
                                "Ember came in. Ember sat. Ember’s coat. Ember left. Ember ran. Dane spoke.",
                                "Dane spoke. Dane spoke. Dane spoke. Dane spoke. Dane spoke. Dane spoke."),
                        "Title"));
        service.add(new GlossaryEntry("p1:ember", PROJECT, "Ember", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        service.add(new GlossaryEntry("p1:dane", PROJECT, "Dane", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        assertThat(openProjects.hasIndex(PROJECT)).isFalse();

        final List<UnknownGender> asked = service.unknownGenders(PROJECT, 5).data();

        assertThat(asked).containsExactly(new UnknownGender("Dane", 7), new UnknownGender("Ember", 5));
        assertThat(openProjects.hasIndex(PROJECT)).isTrue();
    }

    // IF the name scan of an empty glossary runs, THEN it reads the project's index, and the proposals are the same.
    @Test
    void scan_emptyGlossary_readsTheProjectsIndex() {
        openProjects.put(PROJECT, book(repeated("Then we saw Hale there.", 3), "Title"));
        assertThat(openProjects.hasIndex(PROJECT)).isFalse();

        final EntryChanges<GlossaryEntry> added =
                Objects.requireNonNull(service.scan(PROJECT).data());

        assertThat(added.added()).extracting(GlossaryEntry::term).containsExactly("Hale");
        assertThat(openProjects.hasIndex(PROJECT)).isTrue();
    }

    // IF the book is replaced, THEN the old index goes with it and the scan reads the new book's.
    @Test
    void scan_bookReplaced_readsTheNewBooksIndex() {
        openProjects.put(PROJECT, book(repeated("Then we saw Hale there.", 3), "Title"));
        service.scan(PROJECT);
        service.remove(PROJECT, "p1:hale");
        openProjects.put(PROJECT, book(repeated("Then we saw Marta there.", 3), "Title"));

        final EntryChanges<GlossaryEntry> added =
                Objects.requireNonNull(service.scan(PROJECT).data());

        assertThat(added.added()).extracting(GlossaryEntry::term).containsExactly("Marta");
    }
}
