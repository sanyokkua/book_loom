package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/** The Start question's answers: a confirmed or changed gender becomes the person's; a row left alone stays a hint. */
class GlossaryServiceAnswerGendersTest {

    private GlossaryService service;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
    }

    private void likely(final String term, final Gender gender, final double confidence) {
        service.add(NameKnowledgeReplies.recorded(
                        GlossaryEntry.of("p1:" + term, PROJECT, term, null, TermType.CHARACTER, Gender.UNKNOWN, false),
                        new NameKnowledge(gender, TermType.CHARACTER, null, confidence))
                .withGenderFrom(gender, Origin.LIKELY));
    }

    @Test
    void answerGenders_changedAndConfirmed_becomeThePersonsAndARowLeftAloneStaysLikely() {
        likely("Ivar", Gender.MALE, 0.55);
        likely("Mira Holt", Gender.FEMALE, 0.9);
        likely("Sasha", Gender.FEMALE, 0.6);

        final var answered = service.answerGenders(
                PROJECT, Map.of("p1:Ivar", Gender.FEMALE, "p1:Mira Holt", Gender.FEMALE, "gone", Gender.MALE));

        assertThat(answered.isOk()).isTrue();
        assertThat(Objects.requireNonNull(answered.data())).hasSize(2);
        assertThat(Objects.requireNonNull(service.entries(PROJECT).data()))
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactlyInAnyOrder(
                        tuple("Ivar", Gender.FEMALE, Origin.USER),
                        tuple("Mira Holt", Gender.FEMALE, Origin.USER),
                        tuple("Sasha", Gender.FEMALE, Origin.LIKELY));
    }
}
