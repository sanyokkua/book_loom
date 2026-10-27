package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.notify.Severity;
import ua.bookloom.ui.notify.ToastOccasion;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * Where a message goes while a dialog or visible model work is open: into that dialog's own footer line, never a
 * toast, because a message in the corner would land on the dialog's buttons or behind its scrim.
 */
class ToastRoutingTest extends ShellTestBase {

    private static final long WAIT_SECONDS = 3;

    private Toasts toasts() {
        return injector.getInstance(Toasts.class);
    }

    private ModalCard showCard() {
        final ModalCard card = ThemeTestSupport.onFx(() -> {
            final ModalCard made = new ModalCard(ModalCard.Size.DIALOG);
            made.setId("routing-card");
            made.setContent(new Label("body"));
            made.getButtonTypes().add(ButtonType.CLOSE);
            injector.getInstance(ModalHost.class).show(made, false);
            return made;
        });
        interact(() -> {});
        return card;
    }

    private static String footerOf(final Node card) {
        return ThemeTestSupport.onFx(() -> ((Label) card.lookup("#modal-feedback")).getText());
    }

    private int toastCount() {
        return ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll(".toast").size());
    }

    // IF a message raised over an open dialog became a toast, THEN it would sit on the dialog's buttons or behind its
    // scrim; it is the one footer line of the dialog instead.
    @Test
    void raise_whileADialogIsOpen_becomesTheDialogsFooterLineAndNoToast() {
        final ModalCard card = showCard();

        onFx(() -> toasts().raise(ToastOccasion.RUN_STARTED));

        assertThat(footerOf(card)).isEqualTo("Translation started — Progress is saved as you go.");
        assertThat(card.lookup("#modal-feedback").getStyleClass()).contains("dialog-feedback-info");
        assertThat(toastCount()).isZero();
    }

    // IF the dialog stayed registered after it closed, THEN every later message would vanish into a dead footer.
    @Test
    void raise_afterTheDialogClosed_isAToastAgain() {
        showCard();
        onFx(() -> injector.getInstance(ModalHost.class).hide());

        onFx(() -> toasts().raise(ToastOccasion.BOOK_OPENED, "A.epub"));

        assertThat(toastCount()).isEqualTo(1);
    }

    // IF a result were announced with a toast while the progress dialog shows, THEN it would land behind the work
    // layer's scrim; the progress dialog is a dialog too, so its footer takes the line.
    @Test
    void raise_whileModelWorkIsVisible_becomesTheProgressDialogsFooterLine() throws TimeoutException {
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.EXPORT, null));
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> {
                    final Node dialog = scene.getRoot().lookup("#progress-title");
                    return dialog != null && dialog.isVisible();
                }));
        final Node footerHolder = ThemeTestSupport.onFx(() -> scene.getRoot().lookup("#modal-feedback"));

        onFx(() -> toasts().raise(ToastOccasion.MODEL_LIST_UNREADABLE));

        assertThat(footerHolder).isNotNull();
        assertThat(((Label) footerHolder).getText()).isEqualTo("Could not read the model list");
        assertThat(toastCount()).isZero();
    }

    // IF a footer line with no dialog open became a toast, THEN the closed list of occasions would be open again.
    @Test
    void feedback_noDialogOpen_saysNothingAndRaisesNoToast() {
        onFx(() -> toasts().feedback(Severity.SUCCESS, ua.bookloom.ui.i18n.MessageKey.REVIEW_SAVED, "ch5 · p12"));

        assertThat(toastCount()).isZero();
    }

    // IF the footer line lost its severity, THEN a saved segment and a refusal would read alike.
    @Test
    void feedback_whileADialogIsOpen_isItsFooterLineWithItsSeverity() {
        final ModalCard card = showCard();

        onFx(() -> toasts().feedback(Severity.SUCCESS, ua.bookloom.ui.i18n.MessageKey.REVIEW_SAVED, "ch5 · p12"));

        assertThat(footerOf(card)).isEqualTo("Saved — ch5 · p12.");
        assertThat(card.lookup("#modal-feedback").getStyleClass()).contains("dialog-feedback-success");
    }
}
