package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.project;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The glossary's translate-only action writes its own targets (15i.T4), so it applies the lock rule where it writes: a
 * name the policy keeps in Latin letters comes out with its source as target and locked, as after a review, and a
 * name the policy translates comes out as a suggestion only.
 */
class GlossaryTranslateLockTest {

    private Injector injector;
    private GlossaryService service;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(
                new DocumentModule(),
                new PersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
    }

    // IF the translate-only action wrote a kept name as a plain suggestion, THEN the same name would be locked after
    // «Review with model» and unlocked after «Translate», and only one of them would be masked in the draft.
    @Test
    void translate_keepOriginalPolicy_namesTheOrganisationsAndTitlesAreLockedAsTheirSource() {
        openProject(NamePolicy.KEEP_ORIGINAL);
        service.add(open("Weekly Courier Digest", TermType.TITLE));
        service.add(open("KESSEL OPTIK", TermType.OTHER));

        service.translate(PROJECT, new ScriptedChatModel().answer(suggestions()), event -> {});

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(
                        tuple("Weekly Courier Digest", "Weekly Courier Digest", true),
                        tuple("KESSEL OPTIK", "KESSEL OPTIK", true));
    }

    @Test
    void translate_transliteratePolicy_theSameNamesGetOnlyASuggestedUnlockedRendering() {
        openProject(NamePolicy.TRANSLITERATE);
        service.add(open("Weekly Courier Digest", TermType.TITLE));

        service.translate(
                PROJECT,
                new ScriptedChatModel()
                        .answer(Result.ok(new ChatResponse(
                                "{\"suggestions\":[{\"term\":\"Weekly Courier Digest\","
                                        + "\"target\":\"Вікли Курієр Дайджест\"}]}",
                                FinishReason.STOP))),
                event -> {});

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked, GlossaryEntry::isSuggested)
                .containsExactly(tuple("Вікли Курієр Дайджест", false, true));
    }

    private void openProject(final NamePolicy policy) {
        final BookBrief defaults = BookBrief.defaults("en");
        final BookBrief brief = new BookBrief(
                "en",
                "uk",
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                policy,
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                defaults.alsoTranslate(),
                defaults.dial());
        injector.getInstance(ProjectRepository.class).save(project(brief));
        injector.getInstance(OpenProjects.class)
                .put(
                        PROJECT,
                        book(
                                List.of(
                                        "On the train he read the latest Weekly Courier Digest.",
                                        "Nell bought a lens from KESSEL OPTIK on her way home."),
                                "Title"));
    }

    private static GlossaryEntry open(final String term, final TermType type) {
        return new GlossaryEntry(PROJECT + ":" + term, PROJECT, term, null, type, Gender.UNKNOWN, false);
    }

    private static Result<ChatResponse> suggestions() {
        return Result.ok(new ChatResponse("{\"suggestions\":[]}", FinishReason.STOP));
    }
}
