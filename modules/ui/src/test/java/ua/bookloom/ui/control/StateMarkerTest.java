package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.control.StateMarker.Meaning;
import ua.bookloom.ui.i18n.Messages;

/** The six meanings of a state, each an icon, a word and one style class, the same wherever a marker is drawn. */
@SuppressWarnings("NullAway.Init")
class StateMarkerTest extends FxTestBase {

    private static Messages messages(final String language) {
        return new Messages(() -> Locale.forLanguageTag(language));
    }

    @Override
    public void start(final javafx.stage.Stage stage) {
        // The markers are built off-screen; no scene is needed.
    }

    // IF a meaning lost its class or its icon, THEN its colour or its glyph would differ between two screens.
    @ParameterizedTest
    @CsvSource({
        "WAITING, state-waiting, CLOCK",
        "WORKING, state-working, LOADER",
        "DONE, state-done, CHECK_CIRCLE",
        "ATTENTION, state-attention, ALERT_TRIANGLE",
        "FAILED, state-failed, X_CIRCLE",
        "KEPT, state-kept, MINUS_CIRCLE"
    })
    void of_eachMeaning_hasItsStyleClassAndItsFeatherIcon(
            final Meaning meaning, final String styleClass, final Feather icon) {
        final StateMarker marker = ThemeTestSupport.onFx(() -> StateMarker.of(messages("en"), meaning));

        assertThat(marker.getStyleClass()).contains("state-marker", styleClass);
        assertThat(((FontIcon) marker.getGraphic()).getIconCode()).isEqualTo(icon);
    }

    // IF two meanings shared a class, THEN a person could not tell them by colour.
    @Test
    void meanings_all_haveDistinctStyleClassesAndIcons() {
        assertThat(Stream.of(Meaning.values()).map(Meaning::styleClass)).doesNotHaveDuplicates();
        assertThat(Stream.of(Meaning.values()).map(Meaning::icon)).doesNotHaveDuplicates();
        assertThat(Meaning.values()).hasSize(6);
    }

    // IF the words were missing or were the same in two meanings, THEN the colour would carry the state alone.
    @ParameterizedTest
    @CsvSource({
        "en, WAITING, waiting",
        "en, WORKING, working",
        "en, DONE, done",
        "en, ATTENTION, needs attention",
        "en, FAILED, failed",
        "en, KEPT, kept as is",
        "uk, WAITING, очікує",
        "uk, KEPT, залишено як є"
    })
    void of_eachMeaning_showsItsWordInTheDisplayLanguage(
            final String language, final Meaning meaning, final String word) {
        final StateMarker marker = ThemeTestSupport.onFx(() -> StateMarker.of(messages(language), meaning));

        assertThat(marker.getText()).isEqualTo(word);
    }

    // IF a queued item read "translating...", THEN a person would think the model was already at work on it; and IF two
    // meanings shared a word, THEN the word would not tell them apart.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void of_sixMeanings_haveSixDifferentWordsAndWaitingIsNotWorking(final String language) {
        final List<String> words = ThemeTestSupport.onFx(() -> Stream.of(Meaning.values())
                .map(meaning -> StateMarker.of(messages(language), meaning).getText())
                .toList());

        assertThat(words)
                .doesNotHaveDuplicates()
                .allSatisfy(word -> assertThat(word).isNotBlank());
        assertThat(ThemeTestSupport.onFx(() ->
                        StateMarker.of(messages(language), Meaning.WAITING).getText()))
                .doesNotContain("translating");
    }

    // IF a tooltip-less marker were not a Label, THEN it could not sit in a cell like the chips it replaces.
    @Test
    void of_anyMeaning_isALabelThatKeepsItsWholeWord() {
        final StateMarker marker = ThemeTestSupport.onFx(() -> StateMarker.of(messages("en"), Meaning.KEPT));

        assertThat(marker).isInstanceOf(Label.class);
        assertThat(marker.getMinWidth()).isEqualTo(javafx.scene.layout.Region.USE_PREF_SIZE);
    }
}
