package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/**
 * The book map on Names &amp; style (glossary "Show the glossary as an editable table on Names &amp; style", "Show a
 * suggested target as a hint and let the person accept it"): each row shows its forms, the target's state, where each
 * value came from, the review flags and the Unsure mark, and Accept all reports the rows it left. Invented names only.
 */
class GlossaryBookMapScreenTest extends TranslatingScreenTestBase {

    private static final int KIND = 1;
    private static final int FORMS = 2;
    private static final int TARGET = 3;
    private static final int STATE = 4;
    private static final int GENDER = 5;

    private static final GlossaryEntry ZOTOV = GlossaryEntry.of(
                    "e1", "p1", "Зотов", "Зотов", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withForms(List.of("Зотова", "Зотову"))
            .withGenderFrom(Gender.MALE, Origin.BOOK)
            .withUnsureMentions(2);
    private static final GlossaryEntry HALE = GlossaryEntry.of(
                    "e2", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedTarget("Гейл");
    private static final GlossaryEntry VALDOR = GlossaryEntry.of(
                    "e3", "p1", "Valdor", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedTarget("Валдором");

    private void showGlossary(final GlossaryEntry... rows) throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(rows)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    @SuppressWarnings("unchecked")
    private TableView<GlossaryEntry> table() {
        return (TableView<GlossaryEntry>) required("names-style-table");
    }

    private TableCell<?, ?> cell(final String term, final int column) throws TimeoutException {
        awaitFx(() -> rowOf(term) != null);
        return ThemeTestSupport.onFx(() -> (TableCell<?, ?>) Objects.requireNonNull(rowOf(term), "row")
                .getChildrenUnmodifiable()
                .get(column));
    }

    private @Nullable TableRow<?> rowOf(final String term) {
        return table().lookupAll(".table-row-cell").stream()
                .map(node -> (TableRow<?>) node)
                .filter(row -> row.getItem() instanceof GlossaryEntry entry
                        && entry.term().equals(term))
                .findFirst()
                .orElse(null);
    }

    private static List<String> shownTexts(final Node cell, final String styleClass) {
        return ThemeTestSupport.onFx(() -> cell.lookupAll("." + styleClass).stream()
                .filter(Node::isVisible)
                .map(node -> ((Label) node).getText())
                .toList());
    }

    private static String choiceText(final Node cell) {
        return ThemeTestSupport.onFx(() -> ((Label) cell.lookup(".glossary-choice")).getText());
    }

    @Test
    void glossaryTable_lemmaRow_showsFormsStateAndOriginChips() throws Exception {
        showGlossary(ZOTOV);

        final TableCell<?, ?> forms = cell("Зотов", FORMS);
        assertThat(shownTexts(forms, "glossary-forms")).containsExactly("Зотова, Зотову");
        assertThat(TooltipProbe.tipText(forms.lookup(".glossary-forms"))).isEqualTo("joins 2 forms: Зотова, Зотову");
        assertThat(choiceText(cell("Зотов", STATE))).isEqualTo("pinned");
        assertThat(choiceText(cell("Зотов", KIND))).isEqualTo("character · person");
        assertThat(choiceText(cell("Зотов", GENDER))).isEqualTo("male · book");
        assertThat(shownTexts(cell("Зотов", TARGET), "glossary-origin")).containsExactly("person");
    }

    @Test
    void glossaryTable_unsureMention_showsTheMark() throws Exception {
        showGlossary(ZOTOV, HALE);

        final Node mark = cell("Зотов", FORMS).lookup(".glossary-unsure");
        assertThat(shownTexts(cell("Зотов", FORMS), "glossary-unsure")).containsExactly("unsure");
        assertThat(TooltipProbe.tipText(mark)).isEqualTo("Unsure: some mentions fit two names");
        assertThat(shownTexts(cell("Hale", FORMS), "glossary-unsure")).isEmpty();
    }

    @Test
    void glossaryTable_suggestedTarget_showsLikelyAndTickAccepts() throws Exception {
        showGlossary(HALE);
        final TableCell<?, ?> target = cell("Hale", TARGET);
        glossary.willAnswer(Result.ok(HALE.accepted()));

        assertThat(ThemeTestSupport.onFx(() -> target.lookup(".text-field").getPseudoClassStates()))
                .contains(PseudoClass.getPseudoClass("suggested"));
        assertThat(shownTexts(target, "glossary-origin")).containsExactly("likely");
        assertThat(choiceText(cell("Hale", STATE))).isEqualTo("suggested");
        onFx(() -> ((Button) target.lookup(".glossary-accept")).fire());
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated()).containsExactly(HALE.accepted());
        assertThat(glossary.updated().getFirst().targetState()).isEqualTo(TargetState.PINNED);
        // Accepting is the person's decision, so the chip names the person (glossary spec, "A target's state and
        // origin are shown").
        awaitFx(() -> rowOf("Hale") != null
                && rowOf("Hale").getItem() instanceof GlossaryEntry shown
                && shown.targetState() == TargetState.PINNED);
        assertThat(shownTexts(cell("Hale", TARGET), "glossary-origin")).containsExactly("person");
        assertThat(choiceText(cell("Hale", STATE))).isEqualTo("pinned");
    }

    @Test
    void glossaryTable_reviewFlag_showsItsWordsInTheRow() throws Exception {
        glossary.willFlag(Map.of("e3", List.of(TargetIssue.OBLIQUE_ENDING)));
        showGlossary(VALDOR, HALE);

        final TableCell<?, ?> flagged = cell("Valdor", TARGET);
        awaitFx(() -> !shownTexts(flagged, "glossary-review-flag").isEmpty());

        assertThat(shownTexts(cell("Valdor", TARGET), "glossary-review-flag"))
                .containsExactly("not the dictionary form");
        assertThat(shownTexts(cell("Hale", TARGET), "glossary-review-flag")).isEmpty();
    }

    @Test
    void glossaryTable_genderOfAModelAnswer_saysLowConfidenceOrBothValues() throws Exception {
        final GlossaryEntry unsureGuess = GlossaryEntry.of(
                        "e4", "p1", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.FEMALE);
        final GlossaryEntry low =
                withKnowledge(unsureGuess, new NameKnowledge(Gender.FEMALE, TermType.CHARACTER, null, 0.5));
        final GlossaryEntry ruled = withKnowledge(
                GlossaryEntry.of("e5", "p1", "Ivar", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                        .withGenderFrom(Gender.MALE, Origin.RULE),
                new NameKnowledge(Gender.FEMALE, TermType.CHARACTER, null, 0.9));
        showGlossary(low, ruled);

        assertThat(choiceText(cell("Wren", GENDER))).isEqualTo("female · likely, low confidence");
        assertThat(choiceText(cell("Ivar", GENDER))).isEqualTo("rule: male · model: female");
    }

    @Test
    void glossaryTable_acceptAll_sendsRejectedRowsToResultsViewWithUndoAll() throws Exception {
        showGlossary(HALE, VALDOR);
        glossary.willAnswer(Result.ok(new AcceptReport(
                List.of(HALE.accepted()),
                List.of(new AcceptReport.Rejected(VALDOR, List.of(TargetIssue.OBLIQUE_ENDING))))));

        onFx(() -> button("names-style-accept-all").fire());
        awaitFx(() -> scene.getRoot().lookup("#results-undo-all") != null);

        assertThat(glossary.calls()).contains("acceptSuggested(p1)");
        assertThat(ThemeTestSupport.onFx(() -> required("results-list").lookupAll(".label").stream()
                        .map(node -> ((Label) node).getText())
                        .toList()))
                .contains(
                        "Hale",
                        "Гейл · suggested",
                        "Гейл · pinned",
                        "Valdor",
                        "not accepted",
                        "not the dictionary form");
        glossary.willAnswer(Result.ok(HALE));

        onFx(() -> button("results-undo-all").fire());
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated()).containsExactly(HALE);
    }

    @Test
    void acceptAll_placedRightAfterReview() throws Exception {
        showGlossary(HALE);

        final Parent toolbar = (Parent) required("names-style-toolbar");
        final List<String> ids = ThemeTestSupport.onFx(() ->
                toolbar.getChildrenUnmodifiable().stream().map(Node::getId).toList());

        assertThat(ids.indexOf("names-style-accept-all")).isEqualTo(ids.indexOf("names-style-review") + 1);
    }

    private static GlossaryEntry withKnowledge(final GlossaryEntry entry, final NameKnowledge knowledge) {
        return new GlossaryEntry(
                entry.id(),
                entry.projectId(),
                entry.term(),
                entry.forms(),
                entry.lemmaInferred(),
                entry.target(),
                entry.targetState(),
                entry.targetOrigin(),
                entry.type(),
                entry.placeKind(),
                entry.typeOrigin(),
                entry.gender(),
                entry.genderOrigin(),
                entry.genderSeedTried(),
                entry.noGenderEvidence(),
                entry.meaning(),
                entry.meaningChoice(),
                knowledge,
                entry.unsureMentions());
    }
}
