package ua.bookloom.ui.screen;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.TaggedLog;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The translating dashboard's activity log card: its heading, Jump to latest, the errors-only chip and the list. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingLogCard {

    private static final double CARD_SPACING = 8;
    private static final double LOG_HEIGHT = 260;

    static Node build(final TaggedLog logList, final Messages messages) {
        final Label heading = new Label(messages.get(MessageKey.TRANSLATING_LOG_TITLE));
        heading.getStyleClass().add("card-title");
        final ToggleButton errorsOnly = new ToggleButton(messages.get(MessageKey.TRANSLATING_LOG_ERRORS_ONLY));
        errorsOnly.setId("translating-log-errors-only");
        errorsOnly.getStyleClass().add("review-chip");
        Tips.install(messages, errorsOnly, MessageKey.TRANSLATING_LOG_ERRORS_ONLY_TIP);
        errorsOnly.selectedProperty().addListener((observed, was, now) -> {
            log.debug("activity log errors-only {}", now);
            logList.errorsOnlyProperty().set(now);
        });
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(CARD_SPACING, heading, spacer, jumpToLatest(logList, messages), errorsOnly);
        header.setAlignment(Pos.CENTER_LEFT);
        logList.setId("translating-log");
        logList.setPrefHeight(LOG_HEIGHT);
        VBox.setVgrow(logList, Priority.ALWAYS);
        final VBox card = new VBox(CARD_SPACING, header, logList);
        card.setId("translating-log-card");
        card.getStyleClass().add("card");
        return card;
    }

    // Offered only while the person has scrolled the log up, which stops it following its newest line.
    private static Button jumpToLatest(final TaggedLog logList, final Messages messages) {
        final Button jump = Tips.install(
                messages,
                new Button(messages.get(MessageKey.TRANSLATING_LOG_JUMP)),
                MessageKey.TRANSLATING_LOG_JUMP_TIP);
        jump.setId("translating-log-jump");
        jump.getStyleClass().add("review-chip");
        jump.visibleProperty().bind(logList.followingProperty().not());
        jump.managedProperty().bind(jump.visibleProperty());
        jump.setOnAction(event -> logList.jumpToLatest());
        return jump;
    }
}
