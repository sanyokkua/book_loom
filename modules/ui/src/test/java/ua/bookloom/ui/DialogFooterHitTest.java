package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * Every footer button of every dialog in the conformance catalogue answers a real mouse: a press at its centre on the
 * screen reaches the button, not a node laid over it — a footer line once lay over the left-hand buttons, so Undo all
 * and Continue in background took no click.
 */
class DialogFooterHitTest extends ConformanceTestBase {

    static Stream<Arguments> dialogs() {
        return ConformanceCases.SCREENS.stream()
                .filter(screen -> screen.overlay() != Overlay.NONE)
                .map(screen -> Arguments.of(Named.of(screen.name(), screen)));
    }

    // IF a node lay over a footer button, THEN the mouse would press that node and the button would never act.
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialogs")
    void footerButton_pressedAtItsCentre_isWhatTheMouseHits(final Screen screen) throws TimeoutException {
        show(screen);
        final List<Button> buttons = ThemeTestSupport.onFx(() -> FooterHits.footerButtons(scene.getRoot()));

        final List<String> missed = FooterHits.missed(this, scene, buttons);

        assertThat(missed)
                .as("footer buttons the mouse cannot reach on %s", screen.name())
                .isEmpty();
    }

    // IF Undo all took no click, THEN the names a pass added could only be put back one row at a time.
    @Test
    void undoAll_clickedWithTheMouse_revertsEveryRow() throws TimeoutException {
        show(ConformanceResultsCase.RESULTS_DIALOG);
        final Button undoAll = (Button) required("results-undo-all");

        clickOn(undoAll);

        assertThat(undoAll.isDisabled()).as("nothing is left to put back").isTrue();
    }
}
