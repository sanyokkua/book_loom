package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The Connection details fold explains itself on its title only (G2-3: "Shows or hides the fields behind this
 * provider…" stood over the open form wherever the pointer rested in it).
 */
class SettingsConnectionFoldTipTest extends SettingsScreenTestBase {

    private static final String TIP = "Shows or hides the fields behind this provider";
    private static final long WAIT_SECONDS = 3;
    // Longer than the shared show delay, so a tooltip that is going to open has opened.
    private static final long PAST_THE_DELAY_MS = 1200;

    private boolean isFoldTipShowing() {
        return ThemeTestSupport.onFx(() -> Window.getWindows().stream()
                .filter(Tooltip.class::isInstance)
                .map(Tooltip.class::cast)
                .anyMatch(tip -> tip.isShowing() && tip.getText().startsWith(TIP)));
    }

    private Node inFold(final String selector) {
        return ThemeTestSupport.onFx(() -> required("settings-connection").lookup(selector));
    }

    // IF the fold's tip covered the whole open form, THEN it would stand over the fields the person is filling in and
    // stay while the pointer rests anywhere in them; inside the form it never opens.
    @Test
    void hover_insideTheOpenForm_showsNoFoldTip() throws TimeoutException {
        openSettings();
        openFold();

        moveTo(inFold("#settings-checks-hint"));
        WaitForAsyncUtils.sleep(PAST_THE_DELAY_MS, TimeUnit.MILLISECONDS);

        assertThat(isFoldTipShowing()).isFalse();
    }

    // The title still explains the fold, and its tip goes once the pointer leaves the title.
    @Test
    void hover_titleThenAway_showsTheTipThenHidesIt() throws TimeoutException {
        openSettings();

        moveTo(inFold(".title"));
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, this::isFoldTipShowing);
        moveTo(required("settings-checks-hint"));
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> !isFoldTipShowing());

        assertThat(isFoldTipShowing()).isFalse();
    }
}
