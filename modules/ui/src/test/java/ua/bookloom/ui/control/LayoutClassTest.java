package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.control.LayoutClass.Width;

/** The width classes of the content area and the pseudo-classes that carry them to CSS. */
@SuppressWarnings("NullAway.Init")
class LayoutClassTest extends FxTestBase {

    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private static final PseudoClass REGULAR = PseudoClass.getPseudoClass("regular");
    private static final PseudoClass WIDE = PseudoClass.getPseudoClass("wide");

    private StackPane host;

    @Override
    public void start(final Stage stage) {
        host = new StackPane();
        stage.setScene(new Scene(host, 800, 300));
        stage.show();
    }

    // IF the boundaries drifted, THEN a screen would switch layout at a width the specification does not name.
    @ParameterizedTest
    @CsvSource({
        "0, COMPACT",
        "944, COMPACT",
        "1039, COMPACT",
        "1040, REGULAR",
        "1439, REGULAR",
        "1440, WIDE",
        "1920, WIDE"
    })
    void classify_noPreviousClass_usesTheBoundariesAsTheyAre(final double width, final Width expected) {
        assertThat(LayoutClass.next(null, width)).isEqualTo(expected);
    }

    // IF the class flipped back the moment a scrollbar narrowed the area, THEN the layout would flicker at 1040.
    @ParameterizedTest
    @CsvSource({
        "REGULAR, 1039, REGULAR",
        "REGULAR, 1030, REGULAR",
        "REGULAR, 1024, REGULAR",
        "REGULAR, 1023, COMPACT",
        "COMPACT, 1039, COMPACT",
        "COMPACT, 1040, REGULAR",
        "WIDE, 1425, WIDE",
        "WIDE, 1423, REGULAR",
        "WIDE, 1000, COMPACT",
        "COMPACT, 1500, WIDE"
    })
    void next_withAPreviousClass_keepsItInsideTheSixteenPixelBand(
            final Width previous, final double width, final Width expected) {
        assertThat(LayoutClass.next(previous, width)).isEqualTo(expected);
    }

    // IF the pseudo-class did not follow the host's width, THEN the stylesheet could not tell the classes apart.
    @Test
    void install_hostResized_carriesExactlyOnePseudoClassWithHysteresis() {
        interact(() -> LayoutClass.install(host));

        interact(() -> resize(1039));
        assertThat(states()).containsExactly(true, false, false);
        interact(() -> resize(1040));
        assertThat(states()).containsExactly(false, true, false);
        interact(() -> resize(1030));
        assertThat(states()).containsExactly(false, true, false);
        interact(() -> resize(1023));
        assertThat(states()).containsExactly(true, false, false);
        interact(() -> resize(1440));
        assertThat(states()).containsExactly(false, false, true);
    }

    private void resize(final double width) {
        host.setPrefWidth(width);
        host.setMinWidth(width);
        host.setMaxWidth(width);
        host.resize(width, 300);
    }

    private java.util.List<Boolean> states() {
        return java.util.List.of(
                host.getPseudoClassStates().contains(COMPACT),
                host.getPseudoClassStates().contains(REGULAR),
                host.getPseudoClassStates().contains(WIDE));
    }
}
