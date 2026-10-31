package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/** The characters Start asks about: a gender nobody settled, named often enough, least confident first. */
class UnknownGendersTest {

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

    private void add(final String term, final TermType type, final Gender gender) {
        service.add(GlossaryEntry.of("p1:" + term, PROJECT, term, null, type, gender, false));
    }

    // A name met four times is not worth a question; one met five times is, and the more mentioned comes first.
    @Test
    void unknownGenders_fiveOrMoreMentions_listsThemMostMentionedFirst() {
        openProjects.put(
                PROJECT,
                book(
                        List.of(
                                "Ember came in. Ember sat. Ember’s coat. Ember left. Ember ran. Dane spoke.",
                                "Dane spoke. Dane spoke. Dane spoke. Dane spoke. Dane spoke. Dane spoke. Rare met."),
                        "Title"));
        add("Ember", TermType.CHARACTER, Gender.UNKNOWN);
        add("Dane", TermType.CHARACTER, Gender.UNKNOWN);
        add("Rare", TermType.CHARACTER, Gender.UNKNOWN);

        final List<UnknownGender> asked = service.unknownGenders(PROJECT, 5).data();

        assertThat(asked)
                .extracting(UnknownGender::term, UnknownGender::mentions, UnknownGender::offered)
                .containsExactly(tuple("Dane", 7, Gender.UNKNOWN), tuple("Ember", 5, Gender.UNKNOWN));
    }

    // A guessed gender is shown in the table yet still asks the person to confirm it, so Start names it as such; a
    // character with no gender at all is less sure than a guess, so it comes first.
    @Test
    void unknownGenders_suggestedGender_isListedAsSuggestedAndConfirmedOnesAreNot() {
        openProjects.put(
                PROJECT,
                book(
                        List.of(
                                "Ember came in. Ember sat. Ember left. Ember ran. Ember spoke.",
                                "Dane spoke. Dane spoke. Dane spoke. Dane spoke. Dane spoke. Hale came. Hale came."
                                        + " Hale came. Hale came. Hale came."),
                        "Title"));
        add("Ember", TermType.CHARACTER, Gender.UNKNOWN);
        service.add(GlossaryEntry.of("p1:Dane", PROJECT, "Dane", null, TermType.CHARACTER, Gender.MALE, false)
                .withSuggestedGender(Gender.MALE));
        add("Hale", TermType.CHARACTER, Gender.FEMALE);

        final List<UnknownGender> asked = service.unknownGenders(PROJECT, 5).data();

        assertThat(asked)
                .extracting(
                        UnknownGender::term, UnknownGender::suggested, UnknownGender::offered, UnknownGender::origin)
                .containsExactly(
                        tuple("Ember", false, Gender.UNKNOWN, null), tuple("Dane", true, Gender.MALE, Origin.LIKELY));
    }

    // IF the most mentioned came first, THEN a sure guess would bury the doubtful one the person should look at; the
    // least confident comes first, each with its likely value offered (glossary "Ask at Start for the genders nobody
    // has settled").
    @Test
    void start_lowConfidenceFirst_withLikelyPreselected() {
        openProjects.put(
                PROJECT,
                book(
                        List.of(
                                "Mira Holt waved. ".repeat(30) + "Ivar sat. ".repeat(12),
                                "Tobin ran. ".repeat(3) + "Wren sang. ".repeat(40)),
                        "Title"));
        add("Mira Holt", TermType.CHARACTER, Gender.UNKNOWN, Gender.FEMALE, 0.9);
        add("Ivar", TermType.CHARACTER, Gender.UNKNOWN, Gender.MALE, 0.55);
        add("Tobin", TermType.CHARACTER, Gender.UNKNOWN, Gender.MALE, 0.8);
        service.add(GlossaryEntry.of("p1:Wren", PROJECT, "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withGenderFrom(Gender.FEMALE, Origin.BOOK));

        final List<UnknownGender> asked = service.unknownGenders(PROJECT, 5).data();

        assertThat(asked)
                .extracting(
                        UnknownGender::term,
                        UnknownGender::mentions,
                        UnknownGender::entryId,
                        UnknownGender::offered,
                        UnknownGender::origin,
                        UnknownGender::confidence)
                .containsExactly(
                        tuple("Ivar", 12, "p1:Ivar", Gender.MALE, Origin.LIKELY, 0.55),
                        tuple("Mira Holt", 30, "p1:Mira Holt", Gender.FEMALE, Origin.LIKELY, 0.9));
    }

    // A rule and a model that disagree are both shown, the rule's value chosen.
    @Test
    void unknownGenders_ruleTheModelDisagreesWith_offersTheRuleAndNamesTheModel() {
        openProjects.put(PROJECT, book(List.of("Ivar sat. ".repeat(6)), "Title"));
        service.add(NameKnowledgeReplies.recorded(
                GlossaryEntry.of("p1:Ivar", PROJECT, "Ivar", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                        .withGenderFrom(Gender.MALE, Origin.RULE),
                new NameKnowledge(Gender.FEMALE, TermType.CHARACTER, null, 0.8)));

        final List<UnknownGender> asked = service.unknownGenders(PROJECT, 5).data();

        assertThat(asked)
                .extracting(UnknownGender::offered, UnknownGender::origin, UnknownGender::modelGender)
                .containsExactly(tuple(Gender.MALE, Origin.RULE, Gender.FEMALE));
    }

    private void add(
            final String term, final TermType type, final Gender gender, final Gender likely, final double confidence) {
        service.add(NameKnowledgeReplies.recorded(
                        GlossaryEntry.of("p1:" + term, PROJECT, term, null, type, gender, false),
                        new NameKnowledge(likely, type, null, confidence))
                .withGenderFrom(likely, Origin.LIKELY));
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
        openProjects.put(PROJECT, book(List.of("Nothing here."), "Ember Ember Ember Ember Ember Ember"));
        add("Ember", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(service.unknownGenders(PROJECT, 5).data()).isEmpty();
    }

    @Test
    void unknownGenders_noBookOpen_isEmptyNotAnError() {
        add("Ember", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(service.unknownGenders(PROJECT, 5).isOk()).isTrue();
        assertThat(service.unknownGenders(PROJECT, 5).data()).isEmpty();
    }
}
