package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * Start translation with glossary entries that have no target asks first, and the table marks the rows that need a
 * look: an empty target, and a term that looks like junk.
 */
class NamesStyleNoTargetTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry HAS_TARGET =
            new GlossaryEntry("e1", "p1", "Nathaniel", "Натаніель", TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry NO_TARGET =
            new GlossaryEntry("e2", "p1", "Underwood", null, TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final GlossaryEntry BLANK_TARGET =
            new GlossaryEntry("e3", "p1", "Lovelace", "  ", TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final GlossaryEntry SHOUTED =
            new GlossaryEntry("e4", "p1", "CROYDON", "Кройдон", TermType.PLACE, Gender.UNKNOWN, false);
    private static final GlossaryEntry HYPHENATED =
            new GlossaryEntry("e5", "p1", "Bull-head", "Бик", TermType.OTHER, Gender.UNKNOWN, false);

    private static final String CARD = "no-target-card";

    private void showNamesStyle(final GlossaryEntry... entries) throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(entries)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private boolean isAsking() {
        final Node card = scene.getRoot().lookup("#" + CARD);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    @SuppressWarnings("unchecked")
    private TableView<GlossaryEntry> table() {
        return (TableView<GlossaryEntry>) required("names-style-table");
    }

    // IF a start went ahead silently, THEN entries the person forgot would be spelled by the model, differently each
    // time.
    @Test
    void start_entriesWithoutTarget_asksFirstAndListsThem() throws Exception {
        showNamesStyle(HAS_TARGET, NO_TARGET, BLANK_TARGET);

        onFx(() -> button("names-style-start").fire());

        assertThat(isAsking()).isTrue();
        assertThat(textsUnder(required(CARD)))
                .contains("2 entries have no target — the model will choose.", "Review names", "Start anyway");
        assertThat(listedTerms()).containsExactly("Underwood", "Lovelace");
        assertThat(engine.requests()).isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.NAMES_STYLE);
    }

    @Test
    void start_oneEntryWithoutTarget_usesTheSingularWording() throws Exception {
        showNamesStyle(HAS_TARGET, NO_TARGET);

        onFx(() -> button("names-style-start").fire());

        assertThat(textsUnder(required(CARD))).contains("1 entry has no target — the model will choose.");
    }

    @Test
    void start_ukrainianLocale_asksInUkrainian() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        showNamesStyle(HAS_TARGET, NO_TARGET);

        onFx(() -> button("names-style-start").fire());

        assertThat(textsUnder(required(CARD)))
                .contains("1 запис без перекладу — модель обере сама.", "Переглянути імена", "Почати все одно");
    }

    // IF Review names still started the run, THEN the person could not go back and fill the targets in.
    @Test
    void reviewNames_pressed_closesTheQuestionAndStartsNothing() throws Exception {
        showNamesStyle(NO_TARGET);
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("no-target-review").fire());

        assertThat(isAsking()).isFalse();
        assertThat(engine.requests()).isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.NAMES_STYLE);
    }

    @Test
    void startAnyway_pressed_startsOneRunAndShowsTranslating() throws Exception {
        showNamesStyle(NO_TARGET);
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("no-target-start").fire());

        job.awaitRunStarted();
        assertThat(engine.requests()).hasSize(1);
        assertThat(isAsking()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.TRANSLATING);
    }

    @Test
    void start_everyEntryHasATarget_startsWithoutAsking() throws Exception {
        showNamesStyle(HAS_TARGET, SHOUTED);

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
        assertThat(engine.requests()).hasSize(1);
    }

    // IF the likeliest junk were not first under the Check header, THEN a long list would have to be read row by row.
    @Test
    void sortByCheck_mixedRows_listsLikelyJunkFirstThenByTerm() throws Exception {
        showNamesStyle(HAS_TARGET, HYPHENATED, SHOUTED, NO_TARGET);

        onFx(() -> table().getSortOrder().setAll(List.of(table().getColumns().get(5))));

        assertThat(ThemeTestSupport.onFx(() ->
                        table().getItems().stream().map(GlossaryEntry::term).toList()))
                .containsExactly("CROYDON", "Bull-head", "Nathaniel", "Underwood");
    }

    // IF the chips did not follow the rows, THEN a missing target or a likely junk term would look like any other row.
    @Test
    void table_rowsWithAndWithoutFlags_showTheMatchingChips() throws Exception {
        showNamesStyle(HAS_TARGET, SHOUTED, NO_TARGET);

        assertThat(shownFlags()).containsExactlyInAnyOrder("Likely junk", "No target");
    }

    @SuppressWarnings("unchecked")
    private List<String> listedTerms() {
        return ThemeTestSupport.onFx(() -> List.copyOf(((ListView<String>) required("no-target-list")).getItems()));
    }

    private List<String> shownFlags() {
        return ThemeTestSupport.onFx(() -> table().lookupAll(".glossary-flag").stream()
                .filter(Node::isVisible)
                .map(chip -> ((javafx.scene.control.Label) chip).getText())
                .toList());
    }
}
