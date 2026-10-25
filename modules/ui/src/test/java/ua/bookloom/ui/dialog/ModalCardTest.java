package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.ShellTestBase;

/** A dialog card's footer buttons keep their whole words, however long one of them is. */
class ModalCardTest extends ShellTestBase {

    private static final double TOLERANCE = 0.5;

    // IF the footer sized every button alike, THEN the longest label would be cut ("Not stated — s..."), as the
    // narrator
    // card's was.
    @Test
    void footer_oneLongLabel_showsEveryLabelWhole() {
        final List<ButtonType> types = List.of(
                new ButtonType("Back", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType("Female", ButtonBar.ButtonData.OTHER),
                new ButtonType("Male", ButtonBar.ButtonData.OTHER),
                new ButtonType("Not stated — start the translation", ButtonBar.ButtonData.OK_DONE));
        final ModalCard card = onFxCard(types);

        final List<String> cut = ua.bookloom.ui.ThemeTestSupport.onFx(() -> types.stream()
                .map(type -> (Button) card.lookupButton(type))
                .filter(button -> button.getWidth() + TOLERANCE < button.prefWidth(-1))
                .map(Button::getText)
                .toList());

        assertThat(cut).isEmpty();
    }

    // IF the body were not in a scroll pane, THEN a long body would squeeze or overflow the fixed header and footer.
    @Test
    void content_aNode_sitsInAScrollingBody() {
        final ModalCard card = onFxCard(List.of(ButtonType.OK));

        final boolean scrolls = ua.bookloom.ui.ThemeTestSupport.onFx(() -> card.getContent() instanceof ScrollPane);

        assertThat(scrolls).isTrue();
    }

    private ModalCard onFxCard(final List<ButtonType> types) {
        final ModalCard[] made = new ModalCard[1];
        onFx(() -> {
            final ModalCard card = new ModalCard(ModalCard.Size.DIALOG);
            card.getStyleClass().add("dialog-card");
            card.setContent(new Label("The book is told in the first person."));
            card.getButtonTypes().setAll(types);
            card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            injector.getInstance(ModalHost.class).show(card, false);
            made[0] = card;
        });
        onFx(() -> {});
        return made[0];
    }
}
