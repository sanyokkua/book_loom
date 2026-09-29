package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.decided;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.entry;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.repeated;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.project.OpenProjects;

/** The glossary port: the table, the dialog, the removal memory, the scans and the term-change deferrals. */
class GlossaryServiceImplTest {

    private static final String LOCKED_NEEDS_TARGET = "A locked term needs a target.";
    private static final List<String> HALE_BOOK = repeated("Then we saw Hale there.", 3);

    private Injector injector;
    private GlossaryService service;
    private OpenProjects openProjects;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(GlossaryServiceImpl.class);
        openProjects = injector.getInstance(OpenProjects.class);
    }

    @Test
    void add_newTerm_listsItWithThoseValues() {
        final GlossaryEntry justine =
                new GlossaryEntry("p1:justine", PROJECT, "Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true);

        final Result<GlossaryEntry> added = service.add(justine);

        assertThat(added.data()).isEqualTo(justine);
        assertThat(service.entries(PROJECT).data()).containsExactly(justine);
    }

    @Test
    void add_termDifferingOnlyInCase_answersValidationNamingTheTermAndAddsNothing() {
        service.add(entry("Justine", "Жустіна", TermType.CHARACTER, false));

        final Result<GlossaryEntry> refused = service.add(new GlossaryEntry(
                "p1:justine-2", PROJECT, "justine", "Юстина", TermType.CHARACTER, Gender.FEMALE, false));

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(refused.error()).extracting(AppError::message).asString().contains("justine");
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term)
                .containsExactly("Justine");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void add_lockedWithBlankTarget_answersValidationAndAddsNothing(@Nullable final String target) {
        final Result<GlossaryEntry> refused = service.add(entry("Justine", target, TermType.CHARACTER, true));

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(refused.error()).extracting(AppError::message).isEqualTo(LOCKED_NEEDS_TARGET);
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void update_lockingWithEmptyTarget_isRefusedAndTheEntryStaysUnlocked(@Nullable final String target) {
        final GlossaryEntry hale = entry("Hale", target, TermType.CHARACTER, false);
        service.add(hale);

        final Result<GlossaryEntry> refused = service.update(entry("Hale", target, TermType.CHARACTER, true));

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(refused.error()).extracting(AppError::message).isEqualTo(LOCKED_NEEDS_TARGET);
        assertThat(service.entries(PROJECT).data()).containsExactly(hale);
    }

    @Test
    void update_clearingALockedTarget_isRefusedAndTheTargetStays() {
        final GlossaryEntry hale = entry("Hale", "Гейл", TermType.CHARACTER, true);
        service.add(hale);

        final Result<GlossaryEntry> refused = service.update(entry("Hale", "", TermType.CHARACTER, true));

        assertThat(refused.error()).extracting(AppError::message).isEqualTo(LOCKED_NEEDS_TARGET);
        assertThat(service.entries(PROJECT).data()).containsExactly(hale);
    }

    @Test
    void update_targetSetThenLocked_readsBackTargetAndLocked() {
        service.add(entry("Hale", null, TermType.CHARACTER, false));

        service.update(entry("Hale", "Гейл", TermType.CHARACTER, false));
        service.update(entry("Hale", "Гейл", TermType.CHARACTER, true));

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Гейл", true));
    }

    @Test
    void update_unknownEntry_answersValidation() {
        final Result<GlossaryEntry> refused = service.update(entry("Hale", "Гейл", TermType.CHARACTER, false));

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }

    @Test
    void update_lockedTargetChangedOverADecidedSegment_recordsOneTermDeferral() {
        service.add(entry("Hale", "Хейл", TermType.CHARACTER, true));
        injector.getInstance(SegmentRepository.class)
                .saveAll(PROJECT, List.of(decided("ch1:0", "Хейл пішов."), decided("ch1:1", "Ніхто не пішов.")));

        service.update(entry("Hale", "Гейл", TermType.CHARACTER, true));

        final List<Deferral> open =
                injector.getInstance(DeferralRepository.class).open(PROJECT).data();
        assertThat(open)
                .extracting(Deferral::reason, Deferral::segmentId, Deferral::waitingOn, Deferral::replacedRendering)
                .containsExactly(tuple(DeferralReason.TERM, "ch1:0", "Hale", "Хейл"));
    }

    @Test
    void update_targetUnchanged_recordsNoDeferral() {
        service.add(entry("Hale", "Хейл", TermType.CHARACTER, true));
        injector.getInstance(SegmentRepository.class).saveAll(PROJECT, List.of(decided("ch1:0", "Хейл пішов.")));

        service.update(entry("Hale", "Хейл", TermType.PLACE, true));

        assertThat(injector.getInstance(DeferralRepository.class).open(PROJECT).data())
                .isEmpty();
    }

    @Test
    void remove_existingEntry_removesItAndAnswersTrue() {
        service.add(entry("Hale", "Гейл", TermType.CHARACTER, false));

        assertThat(service.remove(PROJECT, "p1:hale").data()).isTrue();
        assertThat(service.remove(PROJECT, "p1:hale").data()).isFalse();
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    @Test
    void scan_emptyGlossary_addsTheProposedNames() {
        openProjects.put(PROJECT, book(HALE_BOOK, "Title"));

        final Result<List<GlossaryEntry>> added = service.scan(PROJECT);

        assertThat(added.data()).extracting(GlossaryEntry::term).containsExactly("Hale");
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term)
                .containsExactly("Hale");
    }

    @Test
    void scan_glossaryHoldingHale_addsNothing() {
        final GlossaryEntry hale = entry("Hale", "Гейл", TermType.CHARACTER, true);
        service.add(hale);
        openProjects.put(PROJECT, book(HALE_BOOK, "Title"));

        final Result<List<GlossaryEntry>> added = service.scan(PROJECT);

        assertThat(added.data()).isEmpty();
        assertThat(service.entries(PROJECT).data()).containsExactly(hale);
    }

    @Test
    void scan_nameOnlyInTheAuxiliaryUnit_isNotProposed() {
        openProjects.put(PROJECT, book(List.of("Then we left."), "Then Acme met Acme and Acme met Acme."));

        assertThat(service.scan(PROJECT).data()).isEmpty();
    }

    @Test
    void scan_projectNotOpen_answersValidation() {
        final Result<List<GlossaryEntry>> refused = service.scan(PROJECT);

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }

    @Test
    void scan_afterThePersonRemovedChapter_doesNotProposeItAndAddingItAgainSucceeds() {
        openProjects.put(PROJECT, book(repeated("Then we read Chapter today.", 5), "Title"));
        service.add(entry("Chapter", null, TermType.OTHER, false));
        service.remove(PROJECT, "p1:chapter");

        final Result<List<GlossaryEntry>> added = service.scan(PROJECT);

        assertThat(added.data()).isEmpty();
        assertThat(service.add(entry("Chapter", "Розділ", TermType.OTHER, false))
                        .isOk())
                .isTrue();
    }

    @Test
    void prescan_briefWithNoTargetLanguage_answersValidationAndMakesNoCall() {
        injector.getInstance(ProjectRepository.class).save(project(BookBrief.defaults("en")));
        openProjects.put(PROJECT, book(repeated("Then we met Moreau there.", 2), "Title"));
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<List<GlossaryEntry>> refused = service.prescan(PROJECT, model);

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void prescan_briefWithTargetLanguage_addsWhatTheModelProposes() {
        final BookBrief brief = withTarget(BookBrief.defaults("en"), "uk");
        injector.getInstance(ProjectRepository.class).save(project(brief));
        openProjects.put(PROJECT, book(List.of("We met Moreau today."), "Title"));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(
                        "{\"terms\":[{\"term\":\"Moreau\",\"type\":\"person\",\"gender\":\"male\"}]}",
                        FinishReason.STOP)));

        final Result<List<GlossaryEntry>> added = service.prescan(PROJECT, model);

        assertThat(added.data())
                .containsExactly(new GlossaryEntry(
                        "p1:moreau", PROJECT, "Moreau", null, TermType.CHARACTER, Gender.MALE, false));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void prescan_projectNotOpen_answersValidation() {
        injector.getInstance(ProjectRepository.class).save(project(withTarget(BookBrief.defaults("en"), "uk")));

        final Result<List<GlossaryEntry>> refused = service.prescan(PROJECT, new ScriptedChatModel());

        assertThat(refused.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }

    @Test
    void entries_repositoryThrows_answersInternalWithTheCause() {
        final GlossaryRepository throwing = (GlossaryRepository) Proxy.newProxyInstance(
                GlossaryRepository.class.getClassLoader(), new Class<?>[] {GlossaryRepository.class}, (p, m, a) -> {
                    throw new IllegalStateException("boom");
                });
        final GlossaryService broken = new GlossaryServiceImpl(
                throwing,
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(DeferralRepository.class),
                injector.getInstance(ProjectRepository.class),
                openProjects,
                injector.getInstance(PreScan.class));

        final Result<List<GlossaryEntry>> failed = broken.entries(PROJECT);

        assertThat(failed.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(failed.error()).extracting(AppError::cause).isInstanceOf(IllegalStateException.class);
    }

    private static Project project(final BookBrief brief) {
        return new Project(PROJECT, Path.of("book.txt"), BookFormat.TXT, "hash", brief);
    }

    private static BookBrief withTarget(final BookBrief brief, final String target) {
        return new BookBrief(
                brief.sourceLanguage(),
                target,
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial());
    }
}
