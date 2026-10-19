package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A keyboard alone walks the Book Brief in the order the two columns are read: the left column's cards top to bottom,
 * then the right column's, then the footer, whether the columns sit side by side or stacked (15i.U4).
 */
class BookBriefFocusTraversalTest extends BookBriefScreenTestBase {

    // Far more presses than the screen has controls: a screen that needs more is trapping the focus.
    private static final int MAX_PRESSES = 200;

    // One control per card in reading order (the narrator's gender is disabled until the narrator is first person).
    private static final List<String> IN_ORDER = List.of(
            "brief-source",
            "brief-target",
            "brief-policy-names",
            "brief-aux-nav",
            "brief-quality",
            "brief-suggest-style",
            "brief-tone-genre",
            "brief-tone-register",
            "brief-tone-voice",
            "brief-tone-audience",
            "brief-tone-narrator",
            "brief-back",
            "brief-continue");

    private Node pressTab() {
        Event.fireEvent(
                scene.getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
        return scene.getFocusOwner();
    }

    private static List<String> idsAbove(final Node owner) {
        final List<String> ids = new ArrayList<>();
        for (Node walk = owner; walk != null; walk = walk.getParent()) {
            if (walk.getId() != null) {
                ids.add(walk.getId());
            }
        }
        return ids;
    }

    private List<String> walkOnceRound() {
        final Node start = ThemeTestSupport.onFx(() -> {
            box("brief-source").getEditor().requestFocus();
            return scene.getFocusOwner();
        });
        final List<String> firstVisits = new ArrayList<>(ThemeTestSupport.onFx(() -> idsAbove(start)));
        final Set<String> seen = new LinkedHashSet<>(firstVisits);
        boolean cameBack = false;
        for (int press = 0; press < MAX_PRESSES && !cameBack; press++) {
            final Node owner = ThemeTestSupport.onFx(this::pressTab);
            for (final String id : ThemeTestSupport.onFx(() -> idsAbove(owner))) {
                if (seen.add(id)) {
                    firstVisits.add(id);
                }
            }
            cameBack = owner.equals(start);
        }
        return cameBack ? firstVisits : List.of();
    }

    // IF the right column were walked before the left, or a card were skipped, THEN a keyboard user would meet the
    // model's predictions before the decisions they depend on, or never reach a control.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void tab_bookOpened_walksLeftColumnThenRightColumnThenFooterAndComesBackRound(
            final double width, final double height) throws TimeoutException {
        openFrankensteinThenShowBrief();
        onFx(() -> briefModel().setTargetLanguage("uk"));
        showAtSceneSize(width, height);

        final List<String> firstVisits = walkOnceRound();

        assertThat(firstVisits)
                .as("Tab came back to the source box within %d presses", MAX_PRESSES)
                .isNotEmpty();
        assertThat(firstVisits.stream().filter(IN_ORDER::contains).toList()).containsExactlyElementsOf(IN_ORDER);
    }
}
