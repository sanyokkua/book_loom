package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.entry;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.project;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.withTarget;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Accept all as a validated action (glossary "Show a suggested target as a hint and let the person accept it", "Lock a
 * suggested rendering only where names do not inflect"): the suggestions that pass are pinned, or locked where they
 * never inflect, and the rest stay suggested with their reasons. Invented names only.
 */
class GlossaryServiceAcceptTest {

    private GlossaryService service;
    private GlossaryRepository repository;
    private ProjectRepository injectorProjects;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
        repository = injector.getInstance(GlossaryRepository.class);
        injectorProjects = injector.getInstance(ProjectRepository.class);
        injectorProjects.save(project(withTarget(BookBrief.defaults("en"), "uk")));
        injector.getInstance(OpenProjects.class)
                .put(PROJECT, book(List.of("Hale and Velk met Valdor by the ZX-81 near Zotov's door."), "Title"));
    }

    @Test
    void acceptSuggested_mixedRows_reportsAcceptedAndRejectedWithReasons() {
        suggest("Hale", "Гейл", TermType.CHARACTER);
        suggest("Velk", "Велк", TermType.CHARACTER);
        suggest("ZX-81", "ZX-81", TermType.OTHER);
        suggest("Valdor", "Валдором", TermType.CHARACTER);
        suggest("Zotov", "Зо\u0301тов", TermType.CHARACTER);
        final GlossaryEntry chosen = entry("Mirel", "Мірел", TermType.CHARACTER, false);
        final GlossaryEntry open = entry("Orrin", null, TermType.CHARACTER, false);
        repository.add(chosen);
        repository.add(open);

        final AcceptReport report =
                Objects.requireNonNull(service.acceptSuggested(PROJECT).data());

        assertThat(report.accepted())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::targetState)
                .containsExactlyInAnyOrder(
                        tuple("Hale", "Гейл", TargetState.PINNED),
                        tuple("Velk", "Велк", TargetState.PINNED),
                        tuple("ZX-81", "ZX-81", TargetState.LOCKED),
                        tuple("Zotov", "Зотов", TargetState.PINNED));
        assertThat(report.rejected())
                .extracting(rejected -> rejected.entry().term(), AcceptReport.Rejected::issues)
                .containsExactly(tuple("Valdor", List.of(TargetIssue.OBLIQUE_ENDING)));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::targetState, GlossaryEntry::targetOrigin)
                .contains(
                        tuple("Hale", TargetState.PINNED, Origin.USER),
                        tuple("Valdor", TargetState.SUGGESTED, Origin.LIKELY))
                .contains(tuple("Mirel", chosen.targetState(), chosen.targetOrigin()))
                .contains(tuple("Orrin", open.targetState(), open.targetOrigin()));
    }

    // Undo all of an Accept all writes each row back as it was before: the store keeps the suggestion it is given.
    @Test
    void update_acceptedRowWrittenBack_isSuggestedAgain() {
        suggest("Hale", "Гейл", TermType.CHARACTER);
        final GlossaryEntry before =
                Objects.requireNonNull(repository.all(PROJECT).data()).getFirst();
        service.acceptSuggested(PROJECT);

        final Result<GlossaryEntry> undone = service.update(before);

        assertThat(undone.isOk()).as("%s", undone.error()).isTrue();
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::targetState, GlossaryEntry::targetOrigin)
                .containsExactly(tuple("Гейл", TargetState.SUGGESTED, Origin.LIKELY));
    }

    // The flags are computed on each call: an oblique-looking target and a lost word are flagged in any state, a clean
    // target and a row with no target are not.
    @Test
    void reviewFlags_heldTargets_flagsObliqueAndWordCountOnly() {
        suggest("Valdor", "Валдором", TermType.CHARACTER);
        repository.add(entry("Varno City", "Варно", TermType.PLACE, false));
        repository.add(entry("Hale", "Гейл", TermType.CHARACTER, false));
        repository.add(entry("Orrin", null, TermType.CHARACTER, false));

        final Result<Map<String, List<TargetIssue>>> flags = service.reviewFlags(PROJECT);

        assertThat(flags.isOk()).as("%s", flags.error()).isTrue();
        assertThat(flags.data())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        GlossaryIds.of(PROJECT, "Valdor"), List.of(TargetIssue.OBLIQUE_ENDING),
                        GlossaryIds.of(PROJECT, "Varno City"), List.of(TargetIssue.WORD_COUNT_DIFFERS)));
    }

    @Test
    void reviewFlags_noTargetLanguage_isEmpty() {
        injectorProjects.save(project(BookBrief.defaults("en")));
        suggest("Valdor", "Валдором", TermType.CHARACTER);

        assertThat(service.reviewFlags(PROJECT).data()).isEmpty();
    }

    @Test
    void acceptSuggested_nothingSuggested_acceptsAndRejectsNothing() {
        repository.add(entry("Mirel", "Мірел", TermType.CHARACTER, false));

        final AcceptReport report =
                Objects.requireNonNull(service.acceptSuggested(PROJECT).data());

        assertThat(report.acceptedCount()).isZero();
        assertThat(report.rejectedCount()).isZero();
    }

    @Test
    void add_stressMarkedTypedTarget_isStoredWithoutTheMark() {
        service.add(entry("Zotov", "Зо\u0301тов", TermType.CHARACTER, false));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target)
                .containsExactly("Зотов");
    }

    // IF stripping a target that is only a soft hyphen left it blank, THEN a locked entry would hold no text to put
    // back.
    @Test
    void add_lockedTargetBlankAfterStripping_isRefused() {
        assertThat(service.add(entry("Zotov", "\u00AD", TermType.CHARACTER, true))
                        .isErr())
                .isTrue();
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    // Closing the question unanswered is transcribe; translate keeps the suggestion as the run's rendering until
    // the next call asks by meaning.
    @Test
    void answerMeaning_threeAnswers_storesEachAndAsksNoMore() {
        suggest("Hale", "Гейл", TermType.CHARACTER);
        suggest("Velk", "Велк", TermType.CHARACTER);
        suggest("Orrin", "Оррін", TermType.PLACE);
        Objects.requireNonNull(repository.all(PROJECT).data())
                .forEach(entry -> repository.update(entry.withMeaningChoice(MeaningChoice.PENDING)));
        final List<GlossaryEntry> asked =
                Objects.requireNonNull(service.meaningQuestions(PROJECT).data());

        final Result<List<GlossaryEntry>> answered = service.answerMeaning(
                PROJECT,
                Map.of(
                        GlossaryIds.of(PROJECT, "Hale"), MeaningChoice.PENDING,
                        GlossaryIds.of(PROJECT, "Velk"), MeaningChoice.TRANSLATE,
                        GlossaryIds.of(PROJECT, "Orrin"), MeaningChoice.TRANSCRIBE_GLOSS,
                        GlossaryIds.of(PROJECT, "Gone"), MeaningChoice.TRANSLATE));

        assertThat(answered.isOk()).as("%s", answered.error()).isTrue();
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::meaningChoice)
                .containsExactlyInAnyOrder(
                        tuple("Hale", "Гейл", MeaningChoice.TRANSCRIBE),
                        tuple("Velk", "Велк", MeaningChoice.TRANSLATE),
                        tuple("Orrin", "Оррін", MeaningChoice.TRANSCRIBE_GLOSS));
        assertThat(asked).hasSize(3);
        assertThat(service.meaningQuestions(PROJECT).data()).isEmpty();
    }

    private void suggest(final String term, final String target, final TermType type) {
        repository.add(entry(term, null, type, false).withSuggestedTarget(target));
    }
}
