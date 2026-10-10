package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The names card and the recurring-terms card offer the same Scan, Review, Translate and Stop bar: the same captions in
 * the same order, each button reaching its own card's service, and one model action at a time across both.
 */
class ModelActionBarTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry HALE =
            new GlossaryEntry("e1", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false);

    @AfterEach
    void releaseTheModel() {
        glossary.releaseModelCalls();
    }

    private void showScreen() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(HALE)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> optional("names-style-actions") != null);
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
    }

    private List<String> idsIn(final String barId) {
        return ua.bookloom.ui.ThemeTestSupport.onFx(() -> ((Pane) required(barId))
                .getChildren().stream().filter(Node::isManaged).map(Node::getId).toList());
    }

    // IF the two cards worded the same action differently, THEN the person would have to learn it twice.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void bar_idle_offersScanReviewTranslateInThatOrderAndNoStop(final String card) throws Exception {
        showScreen();

        assertThat(idsIn(card + "-actions")).containsExactly(card + "-scan", card + "-review", card + "-translate");
        assertThat(button(card + "-scan").getText()).isEqualTo("Scan");
        assertThat(button(card + "-review").getText()).isEqualTo("Review");
        assertThat(button(card + "-translate").getText()).isEqualTo("Translate");
    }

    // IF a button carried another control's words, THEN hovering it would explain the wrong action.
    @ParameterizedTest
    @CsvSource({
        "names-style-scan,NAMES_STYLE_MODEL_SCAN_TIP",
        "names-style-review,NAMES_STYLE_REVIEW_TIP",
        "names-style-translate,NAMES_STYLE_TRANSLATE_TIP",
        "recurring-scan,RECURRING_MODEL_SCAN_TIP",
        "recurring-review,RECURRING_MODEL_REVIEW_TIP",
        "recurring-translate,RECURRING_SUGGEST_TIP"
    })
    void bar_eachButton_explainsItself(final String id, final String tip) throws Exception {
        showScreen();

        assertThat(TooltipProbe.tipText(required(id)))
                .isEqualTo(injector.getInstance(Messages.class).get(MessageKey.valueOf(tip)));
    }

    // IF Translate on the names card did not reach the glossary's translate-only action, THEN it would be a second
    // Review.
    @Test
    void namesTranslate_pressed_asksTheGlossaryToTranslateAndOpensItsResults() throws Exception {
        showScreen();
        final GlossaryEntry translated =
                new GlossaryEntry("e1", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.UNKNOWN, false);
        glossary.willAnswer(Result.ok(
                new EntryChanges<>(List.of(), List.of(), List.of(new EntryChanges.Change<>(HALE, translated, null)))));

        onFx(() -> button("names-style-translate").fire());

        awaitFx(() -> scene.getRoot().lookup("#results-card") != null);
        assertThat(glossary.calls()).contains("translate(p1)");
        assertThat(textsUnder(required("results-card"))).contains("Translating names — results", "Гейл · character");
    }

    @Test
    void termsTranslate_pressed_asksTheLexiconToSuggestRenderings() throws Exception {
        showScreen();
        lexicon.holds(LexiconEntry.of("p1", "master"));
        lexicon.willSuggest(LexiconEntry.of("p1", "master").withSuggested("господар"));

        onFx(() -> button("recurring-translate").fire());

        awaitFx(() -> scene.getRoot().lookup("#results-card") != null);
        assertThat(lexicon.calls()).contains("suggest(p1)");
    }

    // IF a model action on one card left the other card's buttons live, THEN two requests would queue in the gate.
    @Test
    void namesReview_running_disablesBothBarsAndShowsStopOnBoth() throws Exception {
        showScreen();
        glossary.holdModelCalls();

        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> isShown("names-style-stop") && isShown("recurring-stop"));

        assertThat(List.of("names-style", "recurring"))
                .allSatisfy(card -> assertThat(List.of("scan", "review", "translate"))
                        .allSatisfy(what -> assertThat(button(card + "-" + what).isDisabled())
                                .isTrue()));
    }

    // IF the title bar called a term scan a "model scan", THEN the person could not tell which list is being changed.
    @Test
    void chip_namesTranslate_isNamedTranslatingNames() throws Exception {
        showScreen();
        glossary.holdModelCalls();

        onFx(() -> button("names-style-translate").fire());
        awaitFx(() -> isShown("shell-activity"));

        assertThat(labelText("shell-activity-text")).startsWith("Translating names");
    }

    // IF Stop on the terms card did not stop the running action, THEN one of the two Stops would be decoration.
    @Test
    void termsStop_pressedWhileNamesTranslateRuns_stopsItAndHidesStop() throws Exception {
        showScreen();
        glossary.holdModelCalls();
        onFx(() -> button("names-style-translate").fire());
        awaitFx(() -> isShown("recurring-stop"));

        onFx(() -> button("recurring-stop").fire());

        awaitFx(() -> !isShown("recurring-stop"));
        assertThat(isShown("names-style-stop")).isFalse();
    }
}
