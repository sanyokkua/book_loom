package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The Recurring terms card of Names &amp; style: the terms with the rendering later requests keep and what the drafts
 * used, editing a rendering, moving a term to the glossary, and the scan and the typed term. The service is a fake that
 * changes its list as the real one would, so a control is seen to reach it and the row to follow its answer.
 */
class RecurringTermsScreenTest extends TranslatingScreenTestBase {

    private static final LexiconEntry MASTER =
            LexiconEntry.of("p1", "master").seen("господар").seen("господар").seen("учитель");
    private static final LexiconEntry IMP = LexiconEntry.of("p1", "imp");

    private void showCard(final LexiconEntry... held) throws Exception {
        readyToStart();
        lexicon.holds(held);
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of()));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !table().getItems().isEmpty() || held.length == 0);
    }

    private javafx.scene.control.TableView<?> table() {
        return (javafx.scene.control.TableView<?>) required("recurring-table");
    }

    private Stream<Node> inTable(final String selector) {
        return table().lookupAll(selector).stream();
    }

    private TextField renderingOf(final String text) {
        return inTable(".text-field")
                .map(TextField.class::cast)
                .filter(field -> text.equals(field.getText()))
                .findFirst()
                .orElseThrow();
    }

    private Button promoteButton() {
        return inTable(".button")
                .map(Button.class::cast)
                .filter(button -> "To glossary".equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void card_shown_listsEachTermWithItsEstablishedRenderingAndWhatWasUsed() throws Exception {
        showCard(MASTER, IMP);

        assertThat(labelText("recurring-title")).isEqualTo("Recurring terms");
        assertThat(inTable(".glossary-term").map(node -> ((Label) node).getText()))
                .contains("master", "imp");
        assertThat(inTable(".muted").map(node -> ((Label) node).getText()))
                .contains("господар ×2 · учитель ×1", "not used yet");
        assertThat(renderingOf("господар")).isNotNull();
    }

    @Test
    void enter_afterTypingARendering_choosesItThroughTheServiceAndShowsIt() throws Exception {
        showCard(MASTER);
        final TextField field = renderingOf("господар");

        onFx(() -> {
            field.setText("пан");
            field.fireEvent(new ActionEvent());
        });
        awaitFx(() -> lexicon.calls().contains("edit(master, пан)"));

        awaitFx(() -> "пан".equals(renderingOf("пан").getText()));
        assertThat(lexicon.now().getFirst().chosen()).isEqualTo("пан");
    }

    @Test
    void enter_afterClearingTheRendering_givesTheChoiceBack() throws Exception {
        showCard(MASTER.withChosen("пан"));
        final TextField field = renderingOf("пан");

        onFx(() -> {
            field.setText("");
            field.fireEvent(new ActionEvent());
        });
        awaitFx(() -> lexicon.calls().contains("edit(master, null)"));

        assertThat(lexicon.now().getFirst().chosen()).isNull();
    }

    @Test
    void promote_pressed_movesTheTermToTheGlossaryWithItsRenderingAsTheTarget() throws Exception {
        showCard(MASTER);
        final NamesStyleViewModel names = injector.getInstance(NamesStyleViewModel.class);

        onFx(() -> promoteButton().fire());
        awaitFx(() -> lexicon.calls().contains("promote(master)"));

        awaitFx(() -> table().getItems().isEmpty());
        assertThat(names.rows())
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("master", "господар"));
    }

    @Test
    void find_pressed_addsTheTermsTheScanFound() throws Exception {
        showCard();
        lexicon.willFind(IMP);

        onFx(() -> button("recurring-find").fire());

        awaitFx(() -> table().getItems().size() == 1);
        assertThat(lexicon.calls()).contains("scan(p1)");
    }

    @Test
    void add_typedTerm_isAddedAndTheFieldCleared() throws Exception {
        showCard();
        final TextField typed = (TextField) required("recurring-add-field");

        onFx(() -> {
            typed.setText("pentacle");
            button("recurring-add").fire();
        });

        awaitFx(() -> lexicon.calls().contains("add(pentacle)"));
        awaitFx(() -> table().getItems().size() == 1);
        assertThat(typed.getText()).isEmpty();
    }

    @Test
    void add_aTermAlreadyInTheList_isRefusedInPlaceWithoutAskingTheService() throws Exception {
        showCard(IMP);
        final TextField typed = (TextField) required("recurring-add-field");

        onFx(() -> {
            typed.setText("IMP");
            button("recurring-add").fire();
        });

        awaitFx(() -> "IMP is already in the glossary or the list".equals(labelText("names-style-notice-text")));
        assertThat(lexicon.calls()).noneMatch(call -> call.startsWith("add("));
    }
}
