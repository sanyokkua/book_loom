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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/** The characters Start asks about: unknown gender, named often enough, most mentioned first. */
class UnknownGendersTest {

    private GlossaryService service;
    private OpenProjects openProjects;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new PersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
        openProjects = injector.getInstance(OpenProjects.class);
    }

    private void add(final String term, final TermType type, final Gender gender) {
        service.add(new GlossaryEntry("p1:" + term, PROJECT, term, null, type, gender, false));
    }

    // A name met four times is not worth a question; one met five times is, and the more mentioned comes first.
    @Test
    void unknownGenders_fiveOrMoreMentions_listsThemMostMentionedFirst() {
        openProjects.put(
                PROJECT,
                book(
                        List.of(
                                "Chrome came in. Chrome sat. Chrome’s coat. Chrome left. Chrome ran. Finn spoke.",
                                "Finn spoke. Finn spoke. Finn spoke. Finn spoke. Finn spoke. Finn spoke. Rare met."),
                        "Title"));
        add("Chrome", TermType.CHARACTER, Gender.UNKNOWN);
        add("Finn", TermType.CHARACTER, Gender.UNKNOWN);
        add("Rare", TermType.CHARACTER, Gender.UNKNOWN);

        final List<UnknownGender> asked = service.unknownGenders(PROJECT, 5).data();

        assertThat(asked).containsExactly(new UnknownGender("Finn", 7), new UnknownGender("Chrome", 5));
    }

    @Test
    void unknownGenders_knownGenderPlaceAndPartOfAWord_areNotListed() {
        openProjects.put(PROJECT, book(repeated("Anna Annabel Zurich Zurich Zurich Zurich Zurich Zurich.", 2), "x"));
        add("Anna", TermType.CHARACTER, Gender.FEMALE);
        add("Zurich", TermType.PLACE, Gender.UNKNOWN);
        add("Ann", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(service.unknownGenders(PROJECT, 1).data()).isEmpty();
    }

    @Test
    void unknownGenders_namesInAuxiliaryText_areNotCounted() {
        openProjects.put(PROJECT, book(List.of("Nothing here."), "Chrome Chrome Chrome Chrome Chrome Chrome"));
        add("Chrome", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(service.unknownGenders(PROJECT, 5).data()).isEmpty();
    }

    @Test
    void unknownGenders_noBookOpen_isEmptyNotAnError() {
        add("Chrome", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(service.unknownGenders(PROJECT, 5).isOk()).isTrue();
        assertThat(service.unknownGenders(PROJECT, 5).data()).isEmpty();
    }
}
