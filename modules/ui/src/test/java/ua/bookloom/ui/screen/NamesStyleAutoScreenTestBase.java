package ua.bookloom.ui.screen;

import static ua.bookloom.ui.AutoNamesFixture.HALE;
import static ua.bookloom.ui.AutoNamesFixture.MID;
import static ua.bookloom.ui.AutoNamesFixture.PENTACLE;
import static ua.bookloom.ui.AutoNamesFixture.TABLE;
import static ua.bookloom.ui.AutoNamesFixture.WELL;
import static ua.bookloom.ui.AutoNamesFixture.WELL_TYPED;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterEach;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.AutoNamesFixture;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The screen and the scripted pass the "Prepare names automatically" tests (15i.U6) share: Names &amp; style opened over
 * a glossary of {@code Well} and {@code Hale} and one recurring term, {@code table}, with a pass that adds, drops and
 * changes rows in both.
 */
abstract class NamesStyleAutoScreenTestBase extends TranslatingScreenTestBase {

    static final String AUTO = "names-style-auto";
    static final String SUBTITLE = "names-style-auto-subtitle";

    @AfterEach
    void releaseTheModel() {
        namesPass.release();
        glossary.releaseModelCalls();
    }

    void showScreen() throws Exception {
        readyToStart();
        lexicon.holds(TABLE);
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !glossaryTable().getItems().isEmpty()
                && !recurringTable().getItems().isEmpty());
    }

    void showScreenWithoutModel() throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !glossaryTable().getItems().isEmpty());
    }

    @SuppressWarnings("unchecked")
    TableView<GlossaryEntry> glossaryTable() {
        return (TableView<GlossaryEntry>) required("names-style-table");
    }

    @SuppressWarnings("unchecked")
    TableView<LexiconEntry> recurringTable() {
        return (TableView<LexiconEntry>) required("recurring-table");
    }

    List<String> glossaryTerms() {
        return ThemeTestSupport.onFx(() ->
                glossaryTable().getItems().stream().map(GlossaryEntry::term).toList());
    }

    List<String> recurringTerms() {
        return ThemeTestSupport.onFx(() ->
                recurringTable().getItems().stream().map(LexiconEntry::term).toList());
    }

    // Scripts the pass to succeed and to leave both stores as its single write would.
    void passWillSucceed() {
        namesPass.willAnswer(Result.ok(AutoNamesFixture.report()));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));
        glossary.willAnswer(Result.ok(List.of(WELL_TYPED, MID)));
    }

    void pressAuto() {
        onFx(() -> button(AUTO).fire());
    }

    boolean isCardShown(final String cardId) {
        final Node card = scene.getRoot().lookup("#" + cardId);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    boolean isBusyCardShown() {
        final Node host = scene.getRoot().lookup("#shell-busy-host");
        return host != null && host.isVisible() && host.getOpacity() > 0;
    }

    void runToTheResults() throws Exception {
        passWillSucceed();
        pressAuto();
        awaitFx(() -> isCardShown("results-card"));
    }

    Button revertButtonFor(final String term) {
        return ThemeTestSupport.onFx(() -> required("results-list").lookupAll(".button").stream()
                .filter(Node::isVisible)
                .map(Button.class::cast)
                .filter(button -> ("Revert " + term).equals(button.getAccessibleText()))
                .findFirst()
                .orElseThrow());
    }
}
