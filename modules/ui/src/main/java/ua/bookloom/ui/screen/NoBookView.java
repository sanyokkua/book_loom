package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What a screen that is built out of an open book shows while none is open, with the way to the import screen.
 *
 * <p>Built once here so the structure screen reports the same state in the same words: navigation is always live, so
 * either screen can be opened first, and an empty form with a greyed-out picker would look broken where this states
 * the truth with the same number of controls.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NoBookView {

    private static final double CARD_SPACING = 8;
    private static final double CARD_MAX_WIDTH = 720;

    static Node build(final Messages messages, final Navigator navigator) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(navigator, "navigator");
        log.debug("building the no-book state");
        final Label title = new Label(messages.get(MessageKey.NOBOOK_TITLE));
        title.getStyleClass().add("card-title");
        final Label report = new Label(messages.get(MessageKey.NOBOOK_REPORT));
        report.setId("nobook-report");
        report.setWrapText(true);
        report.getStyleClass().add("muted");
        final Button open = new Button(messages.get(MessageKey.NOBOOK_OPEN));
        open.setId("nobook-open");
        open.getStyleClass().add("btn-primary");
        open.setOnAction(event -> {
            log.debug("no book is open: going to the import screen");
            navigator.navigate(ViewNames.IMPORT);
        });
        final VBox card = new VBox(CARD_SPACING, title, report, open);
        card.setId("nobook-card");
        card.getStyleClass().add("card");
        card.setMaxWidth(CARD_MAX_WIDTH);
        return card;
    }
}
