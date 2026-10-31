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
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
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

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
        repository = injector.getInstance(GlossaryRepository.class);
        injector.getInstance(ProjectRepository.class).save(project(withTarget(BookBrief.defaults("en"), "uk")));
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
                .extracting(rejected -> rejected.entry().term(), AcceptReport.Rejected::reasons)
                .containsExactly(tuple("Valdor", List.of("not the dictionary form")));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::targetState, GlossaryEntry::targetOrigin)
                .contains(
                        tuple("Hale", TargetState.PINNED, Origin.USER),
                        tuple("Valdor", TargetState.SUGGESTED, Origin.LIKELY))
                .contains(tuple("Mirel", chosen.targetState(), chosen.targetOrigin()))
                .contains(tuple("Orrin", open.targetState(), open.targetOrigin()));
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

    // IF stripping a target that is only a mark left it blank, THEN a locked entry would hold no text to put back.
    @Test
    void add_lockedTargetBlankAfterStripping_isRefused() {
        assertThat(service.add(entry("Zotov", "\u0301", TermType.CHARACTER, true))
                        .isErr())
                .isTrue();
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    private void suggest(final String term, final String target, final TermType type) {
        repository.add(entry(term, null, type, false).withSuggestedTarget(target));
    }
}
