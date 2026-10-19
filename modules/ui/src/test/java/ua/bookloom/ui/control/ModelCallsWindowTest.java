package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** The window follows its owner through one pair of listeners per open, however often it is opened. */
@SuppressWarnings("NullAway.Init")
class ModelCallsWindowTest extends FxTestBase {

    private static final int PAIR = 2;

    private Stage owner;

    @Override
    public void start(final Stage stage) {
        owner = stage;
        stage.setScene(ThemeTestSupport.themedScene());
        stage.show();
    }

    private ModelCallsWindow window() {
        return ThemeTestSupport.onFx(
                () -> new ModelCallsWindow(new Messages((LocaleProvider) () -> Locale.ENGLISH), this::theme));
    }

    private void theme(final Scene scene) {
        scene.getStylesheets().add(Theme.stylesheet());
    }

    // IF each open added a pair of listeners to the owner, THEN a long session would pile up one pair per open.
    @Test
    void open_openedClosedAndOpenedAgain_holdsOnePairOfOwnerListeners() {
        final ModelCallsWindow calls = window();

        ThemeTestSupport.onFx(() -> {
            calls.open(owner);
            calls.close();
            calls.open(owner);
            return null;
        });

        assertThat(calls.ownerListenerCount()).isEqualTo(PAIR);
        ThemeTestSupport.onFx(() -> {
            calls.close();
            return null;
        });
        assertThat(calls.ownerListenerCount()).isZero();
    }
}
