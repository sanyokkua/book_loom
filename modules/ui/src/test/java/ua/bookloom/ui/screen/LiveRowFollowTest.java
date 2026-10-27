package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.LiveRowFixtures.row;

import java.util.List;
import java.util.stream.IntStream;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRow.Stage;

/**
 * The list of the live card follows the newly translated segment until the person picks another: that pick stops the
 * following and offers Follow again; the switch turns it off for good.
 */
class LiveRowFollowTest extends LiveCardTestBase {

    private static final String FOLLOW = "translating-live-follow";
    private static final String AGAIN = "translating-live-follow-again";

    // The chunk of four: how many of the first rows are translated, the rest in the call.
    private static List<LiveRow> chunk(final int translated) {
        return IntStream.rangeClosed(1, 4)
                .mapToObj(n -> n <= translated ? row(n, Stage.ACCEPTED, "Переклад " + n) : row(n, Stage.IN_CALL, null))
                .toList();
    }

    // IF nothing were selected while the model works, THEN the detail would be empty on the one thing worth reading.
    @Test
    void rows_nothingTranslatedYet_selectTheFirstOneBeingTranslated() {
        showRows(chunk(0));

        assertThat(selectedId()).isEqualTo("s-1");
    }

    // IF the list did not move to each new translation, THEN a person watching would have to scroll after every one.
    @Test
    void newTranslation_followingIsOn_selectsTheNewlyTranslatedRow() {
        showRows(chunk(1));
        assertThat(selectedId()).isEqualTo("s-1");

        publishRows(chunk(2));

        assertThat(selectedId()).isEqualTo("s-2");
    }

    // IF a click were undone by the next translation, THEN a person could never read the segment they chose.
    @Test
    void newTranslation_afterThePersonPickedARow_keepsThePickAndOffersFollowAgain() {
        showRows(chunk(2));
        pick(0);

        publishRows(chunk(3));

        assertThat(selectedId()).isEqualTo("s-1");
        assertThat(isShown(AGAIN)).isTrue();
        assertThat(TooltipProbe.tipText(required(AGAIN)))
                .isEqualTo("Selects the newest translated segment and follows the translation again.");
    }

    // IF Follow again only offered itself, THEN the person would have to scroll to the newest row by hand.
    @Test
    void followAgain_pressed_selectsTheNewestTranslatedRowAndHidesItself() {
        showRows(chunk(2));
        pick(0);
        publishRows(chunk(3));

        onFx(() -> button(AGAIN).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(selectedId()).isEqualTo("s-3");
        assertThat(isShown(AGAIN)).isFalse();
    }

    // IF Follow again were shown while following, THEN it would be a button with nothing to do.
    @Test
    void followAgain_followingFromTheStart_isNotShown() {
        showRows(chunk(1));

        assertThat(isShown(AGAIN)).isFalse();
    }

    // IF the switch did not stop the following, THEN a person who wants a still list could not have one.
    @Test
    void followSwitch_off_leavesTheSelectionWhereItIs() {
        showRows(chunk(1));
        final ToggleSwitch follow = (ToggleSwitch) required(FOLLOW);
        assertThat(follow.isSelected()).isTrue();
        assertThat(TooltipProbe.tipText(follow))
                .isEqualTo("Selects each segment as soon as it is translated, unless you picked one yourself.");

        onFx(() -> follow.setSelected(false));
        publishRows(chunk(2));

        assertThat(selectedId()).isEqualTo("s-1");
    }

    // IF the next chunk took the pick away, THEN a person reading an earlier segment would lose it every few seconds.
    @Test
    void nextChunk_afterAPick_keepsThePickedEarlierRow() {
        showRows(chunk(2));
        pick(0);

        publishRows(concat(chunk(4), List.of(row(5, Stage.ACCEPTED, "Переклад 5"), row(6, Stage.IN_CALL, null))));

        assertThat(selectedId()).isEqualTo("s-1");
        assertThat(isShown(AGAIN)).isTrue();
    }

    // IF the earlier chunks' rows confused the following, THEN it would stay on an old row while new ones are
    // translated.
    @Test
    void nextChunk_following_selectsTheNewTranslationBelowTheHistory() {
        showRows(chunk(4));
        assertThat(selectedId()).isEqualTo("s-4");

        publishRows(concat(chunk(4), List.of(row(5, Stage.ACCEPTED, "Переклад 5"), row(6, Stage.IN_CALL, null))));

        assertThat(selectedId()).isEqualTo("s-5");
    }

    // IF every publication scrolled to the selected row, THEN a person could not scroll back through the history.
    @Test
    void publication_withNothingNewlyTranslated_leavesTheScrollWhereThePersonPutIt() {
        final List<LiveRow> history = IntStream.rangeClosed(1, 40)
                .mapToObj(n -> row(n, Stage.ACCEPTED, "Переклад " + n))
                .toList();
        showRows(history);
        onFx(() -> rowList().scrollTo(0));
        WaitForAsyncUtils.waitForFxEvents();

        publishRows(concat(history, List.of(row(41, Stage.IN_CALL, null))));

        assertThat(cells().getFirst().getIndex()).isZero();
        assertThat(selectedId()).isEqualTo("s-40");
    }

    private static List<LiveRow> concat(final List<LiveRow> first, final List<LiveRow> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    // IF the rows were drawn in a taller cell than the fixed one, THEN a long chunk would lay out slowly.
    @Test
    void list_drawnRows_shareOneFixedCellHeight() {
        showRows(chunk(2));

        assertThat(rowList().getFixedCellSize()).isEqualTo(32.0);
    }
}
