package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The shared hover explanation: its timing and shape, its text from the catalogue, and its reach to column headers. */
@SuppressWarnings("NullAway.Init")
class TipsTest extends ApplicationTest {

    private static Messages messages(final String language) {
        return new Messages(() -> Locale.forLanguageTag(language));
    }

    // IF the delay were shorter or the bubble unbounded, THEN a passing pointer would flash long unwrapped text.
    @Test
    void install_button_attachesAWrappedBoundedTooltipWithADelay() {
        final Button button = ThemeTestSupport.onFx(
                () -> Tips.install(messages("en"), new Button("Add"), MessageKey.NAMES_STYLE_ADD_TIP));

        final Tooltip tooltip = TooltipProbe.tipOf(button).orElseThrow();
        assertThat(tooltip.getShowDelay()).isEqualTo(Duration.millis(400));
        assertThat(tooltip.isWrapText()).isTrue();
        assertThat(tooltip.getMaxWidth()).isEqualTo(320);
        assertThat(tooltip.getShowDuration().toSeconds()).isGreaterThanOrEqualTo(20);
    }

    // IF the text did not come from the catalogue of the display language, THEN a Ukrainian user would read English.
    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void install_button_textIsTheCatalogueEntryOfTheLanguageAndAccessibleHelp(final String language) {
        final Messages messages = messages(language);
        final Button button =
                ThemeTestSupport.onFx(() -> Tips.install(messages, new Button("Add"), MessageKey.NAMES_STYLE_ADD_TIP));

        assertThat(TooltipProbe.tipText(button)).isEqualTo(messages.get(MessageKey.NAMES_STYLE_ADD_TIP));
        assertThat(button.getAccessibleHelp()).isEqualTo(messages.get(MessageKey.NAMES_STYLE_ADD_TIP));
        assertThat(TooltipProbe.tipText(button)).isNotBlank().doesNotContain("namesStyle.add.tip");
    }

    // IF two languages shared one tip text, THEN the Ukrainian catalogue would be an untranslated copy.
    @Test
    void install_sameKey_differsBetweenEnglishAndUkrainian() {
        assertThat(messages("uk").get(MessageKey.NAMES_STYLE_ADD_TIP))
                .isNotEqualTo(messages("en").get(MessageKey.NAMES_STYLE_ADD_TIP));
    }

    // IF a container were not tipped through Tooltip.install, THEN a group of segments could not explain itself.
    @Test
    void install_plainContainer_carriesTheTooltip() {
        final HBox box =
                ThemeTestSupport.onFx(() -> Tips.install(messages("en"), new HBox(), MessageKey.NAMES_STYLE_ADD_TIP));

        assertThat(TooltipProbe.tipText(box)).isEqualTo(messages("en").get(MessageKey.NAMES_STYLE_ADD_TIP));
    }

    // IF a header kept only its text, THEN a column could not explain what belongs in it.
    @Test
    void installOnHeader_column_movesTheCaptionIntoATippedLabel() {
        final TableColumn<String, String> column = ThemeTestSupport.onFx(() -> Tips.installOnHeader(
                messages("en"), new TableColumn<>("Target"), MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP));

        assertThat(column.getGraphic()).isNotNull();
        assertThat(TooltipProbe.headerTipText(column))
                .isEqualTo(messages("en").get(MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP));
    }
}
