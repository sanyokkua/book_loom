package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelChoice;
import ua.bookloom.ui.state.RunStateSource;

/**
 * What Projects shows while nothing is stored: how translating works in four steps, the AI-model line that says
 * whether it can start, and the card's own "New translation". The model line follows the chosen model, through a weak
 * listener this object's field holds because the run state outlives the screen.
 */
@Slf4j
final class ProjectsEmptyView {

    private static final double CARD_SPACING = 12;
    private static final double STEP_SPACING = 10;
    private static final double MODEL_SPACING = 10;
    private static final double STEPS_WIDTH = 560;
    private static final List<MessageKey> STEPS = List.of(
            MessageKey.PROJECTS_EMPTY_STEP1,
            MessageKey.PROJECTS_EMPTY_STEP2,
            MessageKey.PROJECTS_EMPTY_STEP3,
            MessageKey.PROJECTS_EMPTY_STEP4);

    private final Messages messages;
    private final Navigator navigator;
    private final RunStateSource runState;
    private final HBox modelLine = new HBox(MODEL_SPACING);
    private final ChangeListener<ModelChoice> onModel = (observed, was, now) -> showModel(now);

    ProjectsEmptyView(final Messages messages, final Navigator navigator, final RunStateSource runState) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.runState = Objects.requireNonNull(runState, "runState");
    }

    Node build() {
        log.debug("building the projects first-launch card");
        final Label title = new Label(messages.get(MessageKey.PROJECTS_EMPTY_TITLE));
        title.getStyleClass().add("projects-empty-title");
        final VBox steps = new VBox(STEP_SPACING);
        steps.setMaxWidth(STEPS_WIDTH);
        for (int index = 0; index < STEPS.size(); index++) {
            steps.getChildren().add(step(index + 1, STEPS.get(index)));
        }
        final VBox card = new VBox(CARD_SPACING, title, steps);
        card.setAlignment(Pos.TOP_CENTER);
        modelLine.setMaxWidth(STEPS_WIDTH);
        modelLine.getStyleClass().add("banner");
        modelLine.setAlignment(Pos.CENTER_LEFT);
        modelLine.setId("projects-empty-model");
        runState.model().addListener(new WeakChangeListener<>(onModel));
        showModel(runState.model().get());
        card.getChildren().addAll(modelLine, start());
        card.setId("projects-empty-card");
        card.getStyleClass().add("card");
        return card;
    }

    private Node step(final int number, final MessageKey text) {
        final Label badge = new Label(Integer.toString(number));
        badge.getStyleClass().add("count-pill");
        final Label words = new Label(messages.get(text));
        words.setWrapText(true);
        final HBox line = new HBox(STEP_SPACING, badge, words);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private Button start() {
        final Button start = new Button(messages.get(MessageKey.PROJECTS_NEW));
        start.setId("projects-empty-new");
        start.getStyleClass().add("btn-primary");
        Tips.install(messages, start, MessageKey.PROJECTS_NEW_TIP);
        start.setOnAction(event -> {
            log.debug("new translation pressed on the first-launch card");
            navigator.openImportEmpty();
        });
        return start;
    }

    private void showModel(final ModelChoice choice) {
        modelLine.getChildren().clear();
        modelLine.getStyleClass().removeAll("banner-ok", "banner-err");
        modelLine.getStyleClass().add(choice.chosen() ? "banner-ok" : "banner-err");
        if (choice.chosen()) {
            modelLine
                    .getChildren()
                    .addAll(
                            new Label(
                                    messages.get(MessageKey.PROJECTS_MODEL_READY, choice.provider(), choice.modelId())),
                            pusher(),
                            settingsButton(MessageKey.PROJECTS_MODEL_CHANGE, MessageKey.PROJECTS_MODEL_CHANGE_TIP));
        } else {
            modelLine
                    .getChildren()
                    .addAll(
                            new Label(messages.get(MessageKey.NAV_FOOTER_NO_MODEL)),
                            pusher(),
                            settingsButton(
                                    MessageKey.PROJECTS_MODEL_OTHER_PROGRAM,
                                    MessageKey.PROJECTS_MODEL_OTHER_PROGRAM_TIP));
        }
    }

    private static Region pusher() {
        final Region pusher = new Region();
        HBox.setHgrow(pusher, Priority.ALWAYS);
        return pusher;
    }

    private Button settingsButton(final MessageKey caption, final MessageKey tip) {
        final Button button = new Button(messages.get(caption));
        button.getStyleClass().add("btn-secondary");
        Tips.install(messages, button, tip);
        button.setOnAction(event -> {
            log.debug("{} pressed: opening Settings", caption);
            navigator.navigate(ViewNames.SETTINGS);
        });
        return button;
    }
}
