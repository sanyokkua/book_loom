package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The words of Names &amp; style that tell a person what the screen is for: the purpose line at the top, a note on each
 * card, and the empty states, where the empty glossary offers the two ways to fill it.
 */
class NamesStyleTextsScreenTest extends TranslatingScreenTestBase {

    private static final String PURPOSE = "Decide how names and repeated words are translated."
            + " Optional — skip it and BookLoom builds both while it translates.";

    private void showEmpty(final String tag) throws Exception {
        useLocale(Locale.forLanguageTag(tag));
        readyToStart();
        glossary.willAnswer(Result.ok(List.of()));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> optional("names-style-empty-add") != null);
    }

    private double minY(final String id) {
        return ThemeTestSupport.onFx(
                () -> required(id).localToScene(required(id).getBoundsInLocal()).getMinY());
    }

    private String emptyLine(final String tableId) {
        return ThemeTestSupport.onFx(() -> required(tableId).lookupAll(".glossary-empty").stream()
                .filter(Node::isVisible)
                .map(node -> ((Label) node).getText())
                .findFirst()
                .orElseThrow());
    }

    // IF the purpose sat below the cards, THEN a person would meet two empty tables before learning they may skip.
    @Test
    void purpose_projectOpen_readsTheScreenLineAtTheTopAndNoBottomBanner() throws Exception {
        showEmpty("en");

        assertThat(labelText("names-style-purpose")).isEqualTo(PURPOSE);
        assertThat(minY("names-style-purpose")).isLessThan(minY("names-style-card"));
        assertThat(optional("names-style-banner")).isNull();
    }

    @Test
    void purpose_ukrainianInterface_isInUkrainian() throws Exception {
        showEmpty("uk");

        assertThat(labelText("names-style-purpose"))
                .startsWith("Визначте, як перекладати імена та слова, що повторюються.")
                .endsWith("BookLoom створить обидва списки під час перекладу.");
    }

    @Test
    void notes_bothCards_areThePurposeLineOfEach() throws Exception {
        showEmpty("en");

        assertThat(labelText("names-style-note"))
                .isEqualTo("Names of people and places — written the same way every time."
                        + " Set the gender so names take the right grammatical form.");
        assertThat(labelText("recurring-note"))
                .isEqualTo("Words the book repeats — kept the same all through the book."
                        + " Learned while translating, or set here.");
    }

    // IF the empty glossary only said "empty", THEN a first-time person would not see how to fill it.
    @ParameterizedTest
    @CsvSource({
        "en, 'No names yet. Scan the book for them, or add one.', Scan the book, + Add",
        "uk, Імен ще немає. Знайдіть їх у книжці або додайте вручну., Знайти в книжці, + Додати"
    })
    void empty_noNames_readsTheLineWithScanTheBookAndAddButtons(
            final String tag, final String line, final String scan, final String add) throws Exception {
        showEmpty(tag);

        assertThat(emptyLine("names-style-table")).isEqualTo(line);
        assertThat(button("names-style-empty-scan").getText()).isEqualTo(scan);
        assertThat(button("names-style-empty-add").getText()).isEqualTo(add);
    }

    // IF an empty glossary held Start back, THEN the person who skips this step would be stuck on it.
    @Test
    void empty_noNames_startStaysEnabled() throws Exception {
        showEmpty("en");

        assertThat(button("names-style-start").isDisabled()).isFalse();
    }

    @Test
    void emptyAdd_pressed_opensTheAddTermCard() throws Exception {
        showEmpty("en");

        onFx(() -> button("names-style-empty-add").fire());

        awaitFx(() -> optional("add-term-card") != null);
    }

    @Test
    void emptyScan_pressed_runsTheScanOfTheOpenProject() throws Exception {
        showEmpty("en");

        onFx(() -> button("names-style-empty-scan").fire());

        awaitFx(() -> glossary.calls().contains("scan(p1)"));
    }

    @Test
    void empty_noRecurringTerms_readsTheSpecSentence() throws Exception {
        showEmpty("en");

        assertThat(emptyLine("recurring-table"))
                .isEqualTo("No recurring terms yet. They are found when you prepare names, or while translating.");
    }
}
