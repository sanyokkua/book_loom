package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.ScriptedNamesPass;
import ua.bookloom.ui.UiTestInjector;

/** Opening the glossary while its first load is still in the background, with work that waits until the test runs it. */
class NamesStyleOpeningTest extends FxTestBase {

    private static final String PROJECT = "p1";
    private static final GlossaryEntry HALE =
            GlossaryEntry.of("e1", PROJECT, "Hale", null, TermType.OTHER, Gender.UNKNOWN, false);

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    // IF opening the screen twice while the first load is still running started a second scan, THEN the book would be
    // scanned twice at once and the rows would be written twice.
    @Test
    void show_openedTwiceWhileTheLoadRuns_loadsAndScansOnce() {
        final ScriptedGlossaryService glossary = new ScriptedGlossaryService();
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of(HALE)));
        final QueuedExecutor background = new QueuedExecutor();
        final NamesStyleViewModel vm = viewModel(glossary, background);

        interact(() -> {
            vm.show(PROJECT);
            vm.show(PROJECT);
        });
        background.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(glossary.calls()).containsExactly("entries(p1)", "scan(p1)");
        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactly(HALE);
    }

    private static NamesStyleViewModel viewModel(
            final ScriptedGlossaryService glossary, final java.util.concurrent.ExecutorService executor) {
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        final AtomicInteger next = new AtomicInteger();
        return onFx(() -> new NamesStyleViewModel(
                new NamesServices(glossary, new ScriptedLexiconService(), new ScriptedNamesPass()),
                new ModelAccess(ScriptedChatModelFactory.ok(), injector.getInstance(SettingsViewModel.class)),
                executor,
                injector.getInstance(ua.bookloom.ui.i18n.Messages.class),
                injector.getInstance(ActivityTracker.class),
                () -> "new-" + next.incrementAndGet()));
    }
}
