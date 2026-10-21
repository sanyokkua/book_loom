package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.notify.Severity;

/** A dialog card is one of three widths, an XL card has a fixed height, and the footer stays where it is. */
class ModalCardSizeTest extends ShellTestBase {

    private static final double WINDOW_WIDTH = 1280;
    private static final double WINDOW_HEIGHT = 800;

    private ModalCard show(final ModalCard.Size size, final int bodyLines) {
        final ModalCard[] made = new ModalCard[1];
        onFx(() -> {
            final ModalCard card = new ModalCard(size);
            card.getStyleClass().add("dialog-card");
            card.setContent(body(bodyLines));
            card.getButtonTypes().add(ButtonType.OK);
            injector.getInstance(ModalHost.class).show(card, false);
            made[0] = card;
        });
        onFx(() -> {});
        return made[0];
    }

    private static VBox body(final int lines) {
        final VBox box = new VBox();
        for (int i = 0; i < lines; i++) {
            box.getChildren().add(new Label("line " + i));
        }
        return box;
    }

    private double footerY(final ModalCard card) {
        return ua.bookloom.ui.ThemeTestSupport.onFx(() -> {
            final Node bar = card.lookup(".button-bar");
            return bar.localToScene(bar.getBoundsInLocal()).getMinY();
        });
    }

    @Test
    void show_xlCardInA960By640Window_is883By576() {
        resizeScene(960, 640);

        final ModalCard card = show(ModalCard.Size.XL, 3);

        assertThat(card.getWidth()).isEqualTo(883);
        assertThat(card.getHeight()).isEqualTo(576);
    }

    @Test
    void fit_xlCardInALargeWindow_isCappedAt1180By820() {
        final ModalCard card = new ModalCard(ModalCard.Size.XL);

        onFx(() -> card.fit(1920, 1080));

        assertThat(card.getPrefWidth()).isEqualTo(1180);
        assertThat(card.getPrefHeight()).isEqualTo(820);
    }

    @Test
    void footer_bodyGrowsInAnXlCard_staysAtTheSameY() {
        resizeScene(960, 640);
        final ModalCard card = show(ModalCard.Size.XL, 2);
        final double before = footerY(card);

        onFx(() -> card.setContent(body(300)));

        assertThat(footerY(card)).isEqualTo(before);
        assertThat(card.getHeight()).isEqualTo(576);
    }

    @ParameterizedTest
    @CsvSource({"DIALOG,480", "WIDE,720"})
    void show_inA1280Window_hasTheSizesWidth(final ModalCard.Size size, final double expected) {
        resizeScene(WINDOW_WIDTH, WINDOW_HEIGHT);

        final ModalCard card = show(size, 3);

        assertThat(card.getWidth()).isEqualTo(expected);
    }

    @Test
    void show_aDialogCardWithALongBody_staysWithinNinetyPercentOfTheWindowHeight() {
        resizeScene(WINDOW_WIDTH, WINDOW_HEIGHT);

        final ModalCard card = show(ModalCard.Size.DIALOG, 400);

        assertThat(card.getHeight()).isEqualTo(720);
    }

    @Test
    void feedback_aMessage_showsOneLineInTheFooterAndClearsAgain() {
        final ModalCard card = show(ModalCard.Size.DIALOG, 2);

        onFx(() -> card.feedback("Saved", Severity.SUCCESS));
        final String shown =
                ua.bookloom.ui.ThemeTestSupport.onFx(() -> ((Label) card.lookup("#modal-feedback")).getText());
        onFx(card::clearFeedback);
        final String cleared =
                ua.bookloom.ui.ThemeTestSupport.onFx(() -> ((Label) card.lookup("#modal-feedback")).getText());

        assertThat(shown).isEqualTo("Saved");
        assertThat(cleared).isEmpty();
    }
}
