package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** A disabled control's tooltip shows on hover, which JavaFX alone never does, and goes when the pointer leaves. */
@SuppressWarnings("NullAway.Init")
class DisabledTipsTest extends FxTestBase {

    private static final String REASON = "Choose an AI model in Settings › Providers, then start the translation.";
    private static final long WAIT_SECONDS = 3;

    private Button start;
    private Label beside;

    @Override
    public void start(final Stage stage) {
        start = Tips.install(new Button("Start translation"), REASON);
        start.setId("start");
        start.setDisable(true);
        beside = new Label("Ready to translate");
        final HBox row = new HBox(40, start, beside);
        row.setPadding(new Insets(40));
        final Scene scene = new Scene(row, 600, 200);
        DisabledTips.install(scene);
        stage.setScene(scene);
        stage.show();
    }

    private boolean isTipShowing() {
        return ThemeTestSupport.onFx(() -> start.getTooltip().isShowing());
    }

    // IF a disabled Start showed nothing on hover, THEN the person could not learn what is missing.
    @Test
    void hover_disabledButtonWithATip_showsItsTip() throws TimeoutException {
        moveTo(start);

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, this::isTipShowing);
        assertThat(isTipShowing()).isTrue();
    }

    // IF the tip stayed after the pointer left, THEN it would hang over whatever the person points at next.
    @Test
    void hover_pointerLeavesTheDisabledButton_hidesItsTip() throws TimeoutException {
        moveTo(start);
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, this::isTipShowing);

        moveTo(beside);

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> !isTipShowing());
        assertThat(isTipShowing()).isFalse();
    }
}
