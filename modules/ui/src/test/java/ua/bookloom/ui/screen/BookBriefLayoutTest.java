package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.control.TwoColumns;

/**
 * How the Book Brief arranges its cards (15i.U4): what the person decides and the model never touches on the left —
 * Languages, Translation policies, Also translate, Quality vs speed — and the predicted Tone &amp; style with its
 * Suggest button on the right; side by side on a wide window, the left cards above the right one on a narrow window.
 */
class BookBriefLayoutTest extends BookBriefScreenTestBase {

    private static final String COLUMNS = "brief-columns";
    private static final String LEFT = "brief-column-left";
    private static final String RIGHT = "brief-column-right";
    private static final double SAME_ROW = 1.0;
    private static final double BREAKPOINT = 700;
    private static final List<String> LEFT_CARDS =
            List.of("brief-languages-card", "brief-policies-card", "brief-aux-card", "brief-quality-card");
    private static final List<String> PREDICTED_FIELDS = List.of(
            "brief-tone-genre",
            "brief-tone-register",
            "brief-tone-voice",
            "brief-tone-audience",
            "brief-tone-narrator",
            "brief-tone-narrator-gender");

    // IF the cards sat in a plain box, THEN the screen could not give way to a narrow window or use a wide one.
    @Test
    void columns_bookOpened_areATwoColumnsControlThatBreaksAtSevenHundredPixels() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(required(COLUMNS)).isInstanceOf(TwoColumns.class);
        assertThat(ThemeTestSupport.onFx(() ->
                        ((TwoColumns) required(COLUMNS)).breakpointProperty().get()))
                .isEqualTo(BREAKPOINT);
    }

    // IF a decision the model never touches sat among the predicted fields, THEN a person could not tell which cards
    // the Suggest button may fill.
    @ParameterizedTest
    @ValueSource(strings = {"brief-languages-card", "brief-policies-card", "brief-aux-card", "brief-quality-card"})
    void leftColumn_bookOpened_holdsEachDecisionCardAndNeitherTheToneCardNorSuggest(final String cardId)
            throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(isInside(cardId, LEFT)).isTrue();
        assertThat(isInside(cardId, RIGHT)).isFalse();
    }

    // IF a decision card sat on the right, THEN the right column would no longer be only what the model predicts.
    @Test
    void rightColumn_bookOpened_holdsTheToneCardWithItsSuggestButtonAndNoDecisionCard() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(isInside("brief-tone-card", RIGHT)).isTrue();
        assertThat(isInside("brief-suggest-style", RIGHT)).isTrue();
        assertThat(isInside("brief-tone-card", LEFT)).isFalse();
        assertThat(LEFT_CARDS).noneMatch(id -> isInside(id, RIGHT));
    }

    // IF the left column's cards came in another order, THEN the languages would not come first and the quality dial
    // would not come last.
    @Test
    void leftColumn_bookOpened_readsLanguagesPoliciesAlsoTranslateThenQuality() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(LEFT_CARDS.stream().map(id -> boundsOf(id).getMinY()).toList())
                .isSorted();
    }

    // IF the fields came in another order, THEN the card would not read as the Brief's own list of predictions.
    @Test
    void rightColumn_bookOpened_readsSuggestThenGenreRegisterVoiceAudienceNarratorAndItsGender()
            throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(boundsOf("brief-suggest-style").getMaxY())
                .isLessThanOrEqualTo(boundsOf("brief-tone-genre").getMinY());
        assertThat(PREDICTED_FIELDS.stream().map(id -> boundsOf(id).getMinY()).toList())
                .isSorted();
    }

    // IF the cards sat side by side on a window too narrow for both, THEN each would be squeezed until its text was
    // cut.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "960, 640"})
    void columns_narrowWindow_stackWithTheDecisionCardsAboveTheTone(final double width, final double height)
            throws TimeoutException {
        openFrankensteinThenShowBrief();

        showAtSceneSize(width, height);

        final Bounds tone = boundsOf("brief-tone-card");
        assertThat(isStacked(COLUMNS)).isTrue();
        assertThat(tone.getMinY())
                .isGreaterThanOrEqualTo(boundsOf("brief-quality-card").getMaxY());
        assertThat(tone.getMinX()).isCloseTo(boundsOf("brief-languages-card").getMinX(), within(SAME_ROW));
    }

    // IF a wide window kept the cards stacked, THEN the person would scroll past what could sit beside it.
    @Test
    void columns_wideWindow_sitSideBySideOnOneRowWithTheDecisionsFirst() throws TimeoutException {
        openFrankensteinThenShowBrief();

        showAtSceneSize(1440, 900);

        final Bounds languages = boundsOf("brief-languages-card");
        final Bounds tone = boundsOf("brief-tone-card");
        assertThat(isStacked(COLUMNS)).isFalse();
        assertThat(tone.getMinX()).isGreaterThanOrEqualTo(boundsOf(LEFT).getMaxX());
        assertThat(tone.getMinY()).isCloseTo(languages.getMinY(), within(SAME_ROW));
        assertThat(boundsOf("brief-quality-card").getMinX()).isCloseTo(languages.getMinX(), within(SAME_ROW));
    }

    // IF a card dropped out at one size, THEN a choice the run reads could not be made there.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1440, 900"})
    void cards_bookOpened_areAllShownAtBothSizes(final double width, final double height) throws TimeoutException {
        openFrankensteinThenShowBrief();

        showAtSceneSize(width, height);

        assertThat(LEFT_CARDS).allSatisfy(id -> assertThat(isShown(id)).as(id).isTrue());
        assertThat(isShown("brief-tone-card")).isTrue();
    }

    // IF the way on sat beside a column, THEN it would not read as the step's last thing; in the shell the actions
    // live in the window toolbar, outside both scrolling columns, and the subtitle leads the columns.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void footer_bookOpened_actionsLiveInTheToolbarAndTheSubtitleLeadsTheColumns(final double width, final double height)
            throws TimeoutException {
        openFrankensteinThenShowBrief();

        showAtSceneSize(width, height);

        assertThat(boundsOf("brief-subtitle").getMaxY())
                .isLessThanOrEqualTo(boundsOf(COLUMNS).getMinY());
        assertThat(isInside("brief-continue", "shell-actions")).isTrue();
        assertThat(isInside("brief-back", "shell-actions")).isTrue();
        assertThat(isInside("brief-continue", COLUMNS)).isFalse();
    }

    // IF a wide card pushed the content past the viewport, THEN a horizontal scroll bar would hide the right column.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void screen_bookOpened_neverOverflowsTheViewportWidth(final double width, final double height)
            throws TimeoutException {
        openFrankensteinThenShowBrief();

        showAtSceneSize(width, height);

        final ScrollPane scroll = (ScrollPane) required("brief-screen");
        final double content = ThemeTestSupport.onFx(
                () -> scroll.getContent().getLayoutBounds().getWidth());
        final double viewport =
                ThemeTestSupport.onFx(() -> scroll.getViewportBounds().getWidth());
        assertThat(content).isLessThanOrEqualTo(viewport);
    }
}
