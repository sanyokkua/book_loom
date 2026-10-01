package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** What the names and style screen's state does with the glossary service: scans, edits, removals, imports, adds. */
class NamesStyleViewModelTest extends ApplicationTest {

    private static final String PROJECT = "p1";
    private static final String NEEDS_TARGET = "A locked term needs a target.";

    private ScriptedGlossaryService glossary;
    private ScriptedChatModelFactory models;
    private SettingsViewModel settings;
    private NamesStyleViewModel vm;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUpViewModel() {
        glossary = new ScriptedGlossaryService();
        models = ScriptedChatModelFactory.ok();
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        settings = injector.getInstance(SettingsViewModel.class);
        final Messages messages = injector.getInstance(Messages.class);
        final AtomicInteger next = new AtomicInteger();
        vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                models,
                settings,
                new DirectExecutor(),
                messages,
                injector.getInstance(ActivityTracker.class),
                () -> "new-" + next.incrementAndGet()));
    }

    private static GlossaryEntry hale() {
        return new GlossaryEntry("e1", PROJECT, "Hale", null, TermType.OTHER, Gender.UNKNOWN, false);
    }

    private static GlossaryEntry haleTranslated(final boolean locked) {
        return new GlossaryEntry("e1", PROJECT, "Hale", "Гейл", TermType.OTHER, Gender.UNKNOWN, locked);
    }

    private static GlossaryEntry chapter() {
        return new GlossaryEntry("e2", PROJECT, "Chapter", null, TermType.TERM, Gender.NEUTER, false);
    }

    private static AppError refusal() {
        return AppError.of(ErrorCode.validation, "Locked term has no target", NEEDS_TARGET);
    }

    private void show() {
        interact(() -> vm.show(PROJECT));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void showWith(final GlossaryEntry... held) {
        glossary.willAnswer(Result.ok(List.of(held)));
        show();
    }

    private List<GlossaryEntry> rows() {
        return onFx(() -> List.copyOf(vm.rows()));
    }

    private String noticeText() {
        return onFx(() -> vm.notice().get() == null ? "" : vm.notice().get().text());
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF an empty glossary were not scanned when the screen opens, THEN a person would face a blank table.
    @Test
    void show_emptyGlossary_scansOnceAndShowsTheProposedRowUnlocked() {
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of(hale())));

        show();

        assertThat(glossary.calls()).containsExactly("entries(p1)", "scan(p1)");
        assertThat(rows()).containsExactly(hale());
    }

    // IF a glossary the person built were scanned again on opening, THEN their entries would be second-guessed.
    @Test
    void show_glossaryHoldsEntries_neverScansNorPrescans() {
        showWith(hale());

        assertThat(glossary.calls()).containsExactly("entries(p1)");
    }

    // IF an edit did not reach the service, THEN the screen would show what the run never uses.
    @Test
    void setTarget_thenSetLocked_updatesTheEntryEachTime() {
        showWith(hale());
        glossary.willAnswer(Result.ok(haleTranslated(false)));
        glossary.willAnswer(Result.ok(haleTranslated(true)));

        run(() -> vm.setTarget("e1", "Гейл"));
        run(() -> vm.setLocked("e1", true));

        assertThat(glossary.updated()).containsExactly(haleTranslated(false), haleTranslated(true));
        assertThat(rows()).containsExactly(haleTranslated(true));
    }

    // IF a lock with no target were kept on screen, THEN a locked name with nothing to put back would vanish.
    @Test
    void setLocked_noTargetRefused_showsMessageAndKeepsTheSwitchOff() {
        showWith(hale());
        glossary.willAnswer(Result.err(refusal()));

        run(() -> vm.setLocked("e1", true));

        assertThat(noticeText()).isEqualTo(NEEDS_TARGET);
        assertThat(rows()).containsExactly(hale());
    }

    // IF a cleared target of a locked term stayed cleared on screen, THEN the table would disagree with the store.
    @Test
    void setTarget_clearedOnLockedEntryRefused_showsMessageAndRestoresTheTarget() {
        showWith(haleTranslated(true));
        glossary.willAnswer(Result.err(refusal()));

        run(() -> vm.setTarget("e1", " "));

        assertThat(noticeText()).isEqualTo(NEEDS_TARGET);
        assertThat(rows()).containsExactly(haleTranslated(true));
        assertThat(onFx(() -> vm.restorations().get())).isEqualTo(1);
    }

    // IF removing a row did not reach the service, THEN the term would come back at the next opening.
    @Test
    void remove_row_callsRemoveDropsTheRowAndTheNextOpeningScansAgain() {
        showWith(chapter());
        glossary.willAnswer(Result.ok(true));

        run(() -> vm.remove("e2"));
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of()));
        show();

        assertThat(glossary.calls()).containsExactly("entries(p1)", "remove(p1, e2)", "entries(p1)", "scan(p1)");
        assertThat(rows()).isEmpty();
    }

    // IF the model were called before the person asked, THEN the scan would cost minutes unasked.
    @Test
    void modelScan_providerUnreachable_keepsRowsAndPublishesTheProvidersMessage() {
        showWith(hale(), chapter());
        interact(() -> settings.model().set("gemma3:12b"));
        glossary.willAnswer(
                Result.err(AppError.of(ErrorCode.unreachable, "Provider unreachable", "Ollama did not answer.")));

        run(vm::modelScan);

        assertThat(noticeText()).isEqualTo("Ollama did not answer.");
        assertThat(rows()).containsExactly(hale(), chapter());
        assertThat(glossary.calls()).contains("prescan(p1)");
        assertThat(models.selections()).hasSize(1);
    }

    // IF a model scan's proposals were not added, THEN the button would do nothing visible.
    @Test
    void modelScan_answersNewNames_addsThemAfterTheExistingRows() {
        showWith(hale());
        interact(() -> settings.model().set("gemma3:12b"));
        glossary.willAnswer(Result.ok(List.of(chapter())));

        run(vm::modelScan);

        assertThat(rows()).containsExactly(hale(), chapter());
    }

    // IF a scan ran with no model chosen, THEN it would fail on a request nobody can name.
    @Test
    void modelScan_noModelChosen_saysSoAndAsksNothing() {
        showWith(hale());

        run(vm::modelScan);

        assertThat(noticeText()).isEqualTo("Choose a model in the provider settings to run the model scan.");
        assertThat(glossary.calls()).containsExactly("entries(p1)");
    }

    // IF a bad line dropped the good ones, or went unreported, THEN an import would lose names silently.
    @Test
    void importCsv_malformedAndRefusedLines_reportsBothAndKeepsTheImportedRows() {
        showWith(hale());
        glossary.willAnswer(Result.ok(new GlossaryImportReport(2, List.of(3), List.of(5))));
        glossary.willAnswer(Result.ok(List.of(hale(), chapter())));

        run(() -> vm.importCsv(Path.of("names.csv")));

        assertThat(noticeText())
                .isEqualTo("Imported 2 rows. Malformed line: 3. Refused line (locked with no target): 5.");
        assertThat(rows()).containsExactly(hale(), chapter());
    }

    // IF a duplicate were sent to the service, THEN the dialog would learn of it only by a round trip.
    @Test
    void add_termHeldIgnoringCase_refusesWithoutAskingTheService() {
        showWith(new GlossaryEntry("e3", PROJECT, "Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true));
        final List<String> refused = new java.util.concurrent.CopyOnWriteArrayList<>();

        run(() -> vm.add(
                new NewTerm("justine", "Юстина", TermType.CHARACTER, Gender.FEMALE, false), () -> {}, refused::add));

        assertThat(refused).containsExactly("justine is already in the glossary");
        assertThat(glossary.added()).isEmpty();
    }

    // IF a possessive spelling of a held name were added, THEN the glossary would hold one name twice.
    @Test
    void add_possessiveOfAHeldTerm_refusesWithoutAskingTheService() {
        showWith(new GlossaryEntry("e3", PROJECT, "Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true));
        final List<String> refused = new java.util.concurrent.CopyOnWriteArrayList<>();

        run(() ->
                vm.add(new NewTerm("Justine’s", "", TermType.CHARACTER, Gender.FEMALE, false), () -> {}, refused::add));

        assertThat(refused).containsExactly("Justine’s is already in the glossary");
        assertThat(glossary.added()).isEmpty();
    }

    // IF a lock with no target were accepted from the dialog, THEN the same refusal as the table's would be missing.
    @Test
    void add_lockedWithNoTarget_refusesWithoutAskingTheService() {
        showWith();
        final List<String> refused = new java.util.concurrent.CopyOnWriteArrayList<>();

        run(() -> vm.add(new NewTerm("Justine", "", TermType.CHARACTER, Gender.FEMALE, true), () -> {}, refused::add));

        assertThat(refused).containsExactly(NEEDS_TARGET);
        assertThat(glossary.added()).isEmpty();
    }

    // IF an accepted term were not stored with a fresh id and shown, THEN the dialog would close on nothing.
    @Test
    void add_newTerm_storesItUnderANewIdAndShowsTheRow() {
        showWith();
        final GlossaryEntry stored =
                new GlossaryEntry("new-1", PROJECT, "Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true);
        glossary.willAnswer(Result.ok(stored));
        final AtomicInteger added = new AtomicInteger();

        run(() -> vm.add(
                new NewTerm("Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true),
                added::incrementAndGet,
                message -> {}));

        assertThat(glossary.added()).containsExactly(stored);
        assertThat(added).hasValue(1);
        assertThat(rows()).containsExactly(stored);
    }
}
