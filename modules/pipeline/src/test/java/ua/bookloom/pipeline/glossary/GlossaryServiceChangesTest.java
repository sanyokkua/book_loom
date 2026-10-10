package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.project.OpenProjects;

/** What the glossary's model actions report they changed, and the translate-only action. */
class GlossaryServiceChangesTest {

    private final List<JobEvent> events = new ArrayList<>();
    private Injector injector;
    private GlossaryService service;
    private OpenProjects openProjects;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(
                new DocumentModule(),
                new PersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
        openProjects = injector.getInstance(OpenProjects.class);
    }

    @Test
    void review_removedTerm_reportsTheRemovalWithTheModelsQuotedEvidenceAsReason() {
        injector.getInstance(ProjectRepository.class).save(project(withTarget(BookBrief.defaults("en"), "uk")));
        openProjects.put(PROJECT, book(List.of("Well, we met Moreau today."), "Title"));
        final GlossaryEntry well =
                new GlossaryEntry("p1:well", PROJECT, "Well", null, TermType.OTHER, Gender.UNKNOWN, false);
        service.add(well);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(
                        "{\"verdicts\":[{\"term\":\"Well\",\"verdict\":\"not-a-name\",\"type\":\"other\","
                                + "\"gender\":\"unknown\",\"evidence\":\"Well, we met Moreau\"}]}",
                        FinishReason.STOP)));

        final GlossaryReviewReport report = Objects.requireNonNull(
                service.review(PROJECT, model, events::add).data());

        assertThat(report.changes().removed()).containsExactly(new EntryChanges.Removal<>(well, "Well, we met Moreau"));
        assertThat(report.changes().added()).isEmpty();
        assertThat(report.changes().changed()).isEmpty();
    }

    @Test
    void translate_openTermsOnly_givesThemSuggestedTargetsAndTouchesNothingElse() {
        injector.getInstance(ProjectRepository.class).save(project(withTarget(BookBrief.defaults("en"), "uk")));
        openProjects.put(PROJECT, book(List.of("We met Moreau near the Dome today."), "Title"));
        final GlossaryEntry chosen = entry("Hale", "Гейл", TermType.CHARACTER, false);
        final GlossaryEntry open =
                new GlossaryEntry("p1:dome", PROJECT, "Dome", null, TermType.TERM, Gender.UNKNOWN, false);
        service.add(chosen);
        service.add(open);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(
                        "{\"suggestions\":[{\"term\":\"Dome\",\"target\":\"Купол\"}]}", FinishReason.STOP)));

        final EntryChanges<GlossaryEntry> changes = Objects.requireNonNull(
                service.translate(PROJECT, model, events::add).data());

        assertThat(changes.added()).isEmpty();
        assertThat(changes.removed()).isEmpty();
        assertThat(changes.changed())
                .containsExactly(new EntryChanges.Change<>(open, open.withSuggestedTarget("Купол"), null));
        assertThat(model.requests()).hasSize(1);
        assertThat(service.entries(PROJECT).data()).contains(chosen);
        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .extracting(event -> ((ModelCallStarted) event).kind())
                .containsExactly(CallKind.SUGGEST_TARGETS);
    }

    @Test
    void translate_nothingOpen_makesNoCallAndChangesNothing() {
        injector.getInstance(ProjectRepository.class).save(project(withTarget(BookBrief.defaults("en"), "uk")));
        openProjects.put(PROJECT, book(List.of("We met Hale today."), "Title"));
        service.add(entry("Hale", "Гейл", TermType.CHARACTER, false));
        final ScriptedChatModel model = new ScriptedChatModel();

        final EntryChanges<GlossaryEntry> changes = Objects.requireNonNull(
                service.translate(PROJECT, model, events::add).data());

        assertThat(changes.isEmpty()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void translate_modelAnswerFails_writesNothingAndAnswersTheError() {
        injector.getInstance(ProjectRepository.class).save(project(withTarget(BookBrief.defaults("en"), "uk")));
        openProjects.put(PROJECT, book(List.of("We met Dome today."), "Title"));
        service.add(new GlossaryEntry("p1:dome", PROJECT, "Dome", null, TermType.TERM, Gender.UNKNOWN, false));
        final AppError failure = AppError.of(ErrorCode.unreachable, "Offline", "The provider is unreachable.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(failure));

        final Result<EntryChanges<GlossaryEntry>> result = service.translate(PROJECT, model, events::add);

        assertThat(result.error()).isEqualTo(failure);
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target)
                .containsOnlyNulls();
    }
}
