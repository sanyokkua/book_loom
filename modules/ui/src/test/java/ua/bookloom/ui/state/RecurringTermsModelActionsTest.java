package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The Recurring terms card's model scan and review: their rows, their words, and what they need to start. */
class RecurringTermsModelActionsTest extends FxTestBase {

    private static final String PROJECT = "p1";

    private ScriptedLexiconService lexicon;
    private SettingsViewModel settings;
    private NamesStyleViewModel vm;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUp() {
        lexicon = new ScriptedLexiconService();
        final ScriptedGlossaryService glossary = new ScriptedGlossaryService();
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        settings = injector.getInstance(SettingsViewModel.class);
        vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                lexicon,
                ScriptedChatModelFactory.ok(),
                settings,
                new DirectExecutor(),
                injector.getInstance(Messages.class),
                injector.getInstance(ActivityTracker.class),
                () -> "new"));
        glossary.willAnswer(Result.ok(List.of()));
        run(() -> vm.show(PROJECT));
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String notice() {
        return onFx(() -> vm.notice().get().text());
    }

    // IF the scan's result were not worded, THEN rows would appear with no word of where they came from.
    @Test
    void scanWithModel_modelFindsTerms_addsRowsAndSaysHowMany() {
        interact(() -> settings.model().set("gemma3:12b"));
        lexicon.modelWillFind(LexiconEntry.of(PROJECT, "pentacle"), LexiconEntry.of(PROJECT, "imp"));

        run(() -> vm.recurring().scanWithModel());

        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("pentacle", "imp");
        assertThat(notice()).isEqualTo("The model added 2 recurring terms.");
    }

    @Test
    void reviewWithModel_modelDropsATerm_removesItAndSaysHowMany() {
        interact(() -> settings.model().set("gemma3:12b"));
        lexicon.holds(LexiconEntry.of(PROJECT, "pentacle"), LexiconEntry.of(PROJECT, "table"));
        run(() -> vm.show(PROJECT));
        lexicon.reviewWillDrop("table");

        run(() -> vm.recurring().reviewWithModel());

        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("pentacle");
        assertThat(notice()).isEqualTo("The model removed 1 term that is not worth tracking.");
    }

    @Test
    void scanWithModel_noModelChosen_saysSoAndAsksNothing() {
        run(() -> vm.recurring().scanWithModel());

        assertThat(notice()).isEqualTo("Choose a model in the provider settings to scan or review recurring terms.");
        assertThat(lexicon.calls()).doesNotContain("scanWithModel(p1)");
    }
}
