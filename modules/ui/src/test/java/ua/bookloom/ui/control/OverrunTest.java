package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;

/** A one-line label carries its whole text as a tooltip only while the text is cut. */
@SuppressWarnings("NullAway.Init")
class OverrunTest extends FxTestBase {

    private static final String LONG = "Earth Gravity · EN → UK · 26b Max quality";
    private static final String SHORT = "Velk · EN → UK";

    private Label label;

    @Override
    public void start(final Stage stage) {
        label = new Label(LONG);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMinWidth(0);
        final StackPane box = new StackPane(label);
        stage.setScene(new Scene(box, 600, 100));
        stage.show();
        Overrun.install(label);
    }

    private void width(final double width) {
        interact(() -> {
            label.setMaxWidth(width);
            label.setPrefWidth(width);
            label.getScene().getRoot().applyCss();
            label.getScene().getRoot().layout();
        });
    }

    // IF a cut name had no tooltip, THEN the rest of the name could not be read at all.
    @Test
    void install_textDoesNotFit_showsTheWholeTextAsTooltip() {
        width(80);

        assertThat(label.getTooltip()).isNotNull();
        assertThat(label.getTooltip().getText()).isEqualTo(LONG);
    }

    // IF a fitting name still had a tooltip, THEN every label would pop up noise.
    @Test
    void install_textFits_hasNoTooltip() {
        width(500);

        assertThat(label.getTooltip()).isNull();
    }

    // IF the tooltip stayed after the label widened, THEN a whole name would still pop a duplicate.
    @Test
    void install_widenedAfterBeingCut_dropsTheTooltip() {
        width(80);
        width(500);

        assertThat(label.getTooltip()).isNull();
    }

    // IF the tooltip kept the old text, THEN it would explain a name that is no longer there.
    @Test
    void install_textChangedWhileCut_followsTheText() {
        width(80);
        interact(() -> label.setText(LONG + " (copy)"));
        interact(() -> label.getScene().getRoot().layout());

        assertThat(label.getTooltip().getText()).isEqualTo(LONG + " (copy)");
    }

    // IF a short text under a narrow label were judged by its old width, THEN the tooltip would stay.
    @Test
    void install_shortTextBecomesCurrent_hasNoTooltip() {
        width(80);
        interact(() -> label.setText(SHORT));
        width(300);

        assertThat(label.getTooltip()).isNull();
    }
}
