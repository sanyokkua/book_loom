package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/**
 * A target the model suggested reads as one in the glossary table — slanted, badged AI, with a tick that accepts it — and
 * the toolbar's Accept all and the start notice act on every such row.
 */
class GlossarySuggestionsScreenTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry HALE = new GlossaryEntry(
                    "e1", "p1", "Hale", null, TermType.CHARACTER, Gender.MALE, false)
            .withSuggestedTarget("Гейл");
    private static final GlossaryEntry MILTON =
            new GlossaryEntry("e2", "p1", "Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false);

    private void showGlossary(final GlossaryEntry... rows) throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(rows)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private List<Node> rowParts(final String styleClass) {
        return List.copyOf(required("names-style-table").lookupAll("." + styleClass));
    }

    private TextField targetOf(final String text) {
        return rowParts("text-field").stream()
                .map(TextField.class::cast)
                .filter(field -> text.equals(field.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void row_suggestedTarget_isSlantedBadgedAndOffersTheTick() throws Exception {
        showGlossary(HALE, MILTON);

        final TextField suggested = targetOf("Гейл");
        final TextField persons = targetOf("Мілтон");

        assertThat(suggested.getPseudoClassStates()).contains(PseudoClass.getPseudoClass("suggested"));
        assertThat(persons.getPseudoClassStates()).doesNotContain(PseudoClass.getPseudoClass("suggested"));
        assertThat(rowParts("glossary-suggested-badge"))
                .filteredOn(Node::isVisible)
                .extracting(node -> ((Label) node).getText())
                .containsExactly("AI");
        assertThat(rowParts("glossary-accept")).filteredOn(Node::isVisible).hasSize(1);
        assertThat(ThemeTestSupport.onFx(() -> TooltipProbe.tipText(
                        rowParts("glossary-suggested-badge").getFirst())))
                .startsWith("Suggested by the model and not yet confirmed.");
    }

    @Test
    void tick_pressed_storesTheSuggestionAsThePersons() throws Exception {
        showGlossary(HALE, MILTON);
        glossary.willAnswer(Result.ok(HALE.accepted()));
        final Button tick = (Button) rowParts("glossary-accept").stream()
                .filter(Node::isVisible)
                .findFirst()
                .orElseThrow();

        onFx(tick::fire);
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated())
                .extracting(GlossaryEntry::target, GlossaryEntry::origin)
                .containsExactly(tuple("Гейл", TargetOrigin.PERSON));
    }

    @Test
    void enter_onAnUntouchedSuggestion_acceptsIt() throws Exception {
        showGlossary(HALE, MILTON);
        glossary.willAnswer(Result.ok(HALE.accepted()));

        onFx(() -> targetOf("Гейл").fireEvent(new ActionEvent()));
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated()).containsExactly(HALE.accepted());
    }

    @Test
    void enter_afterTypingOverASuggestion_storesThePersonsNewTarget() throws Exception {
        showGlossary(HALE, MILTON);
        glossary.willAnswer(Result.ok(HALE.withTarget("Хейл")));
        final TextField field = targetOf("Гейл");

        onFx(() -> {
            field.setText("Хейл");
            field.fireEvent(new ActionEvent());
        });
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated()).containsExactly(HALE.withTarget("Хейл"));
    }

    @Test
    void acceptAll_noSuggestion_isNotShownButKeepsItsTip() throws Exception {
        showGlossary(MILTON);

        assertThat(button("names-style-accept-all").isVisible()).isFalse();
        assertThat(button("names-style-accept-all").getText()).isEqualTo("Accept all");
        assertThat(TooltipProbe.tipText(required("names-style-accept-all"))).startsWith("Confirms every target");
    }

    @Test
    void acceptAll_pressed_storesEverySuggestionAsThePersons() throws Exception {
        showGlossary(HALE, MILTON);
        glossary.willAnswer(Result.ok(HALE.accepted()));

        onFx(() -> button("names-style-accept-all").fire());
        awaitFx(() -> !button("names-style-accept-all").isVisible());

        assertThat(glossary.updated()).containsExactly(HALE.accepted());
    }

    // IF a run started on unconfirmed suggestions without a word, THEN the person would not know they are only hints.
    @Test
    void startTranslation_withASuggestion_saysItIsUsedAsAHint() throws Exception {
        showGlossary(HALE, MILTON);

        onFx(() -> button("names-style-start").fire());
        job.awaitRunStarted();

        assertThat(ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll(".toast-info").stream()
                        .flatMap(toast -> toast.lookupAll(".toast-text").stream())
                        .map(text -> ((Label) text).getText())
                        .toList()))
                .contains("1 suggested target is unreviewed and will be used as hints.");
    }
}
