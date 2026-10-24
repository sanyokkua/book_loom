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
import ua.bookloom.ui.state.LocalModelProbe;
import ua.bookloom.ui.state.ModelChoice;
import ua.bookloom.ui.state.ProbeOutcome;
import ua.bookloom.ui.state.ProviderNames;
import ua.bookloom.ui.state.RunStateSource;

/**
 * What Projects shows while nothing is stored: how translating works in four steps, the AI-model line that says
 * whether it can start, and the card's own "New translation". The model line follows the chosen model and the look at
 * the local programs, through weak listeners this object's fields hold because both outlive the screen.
 */
@Slf4j
final class ProjectsEmptyView {

    private static final double CARD_SPACING = 12;
    private static final double STEP_SPACING = 10;
    private static final double MODEL_SPACING = 10;
    private static final double NONE_SPACING = 4;
    private static final double STEPS_WIDTH = 560;
    private static final List<MessageKey> STEPS = List.of(
            MessageKey.PROJECTS_EMPTY_STEP1,
            MessageKey.PROJECTS_EMPTY_STEP2,
            MessageKey.PROJECTS_EMPTY_STEP3,
            MessageKey.PROJECTS_EMPTY_STEP4);

    private final Messages messages;
    private final Navigator navigator;
    private final RunStateSource runState;
    private final LocalModelProbe probe;
    private final HBox modelLine = new HBox(MODEL_SPACING);
    private final ChangeListener<ModelChoice> onModel = (observed, was, now) -> showModel();
    private final ChangeListener<ProbeOutcome> onOutcome = (observed, was, now) -> showModel();

    ProjectsEmptyView(
            final Messages messages,
            final Navigator navigator,
            final RunStateSource runState,
            final LocalModelProbe probe) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.runState = Objects.requireNonNull(runState, "runState");
        this.probe = Objects.requireNonNull(probe, "probe");
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
        probe.outcome().addListener(new WeakChangeListener<>(onOutcome));
        showModel();
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

    private void showModel() {
        final ModelChoice choice = runState.model().get();
        final ProbeOutcome outcome = probe.outcome().get();
        modelLine.getChildren().clear();
        modelLine.getStyleClass().removeAll("banner-ok", "banner-err");
        // While the person is asked which of several models to use, nothing is missing yet: the line still looks.
        if (outcome instanceof ProbeOutcome.Looking || outcome instanceof ProbeOutcome.Asking) {
            log.debug("model line: looking, the model question open {}", outcome instanceof ProbeOutcome.Asking);
            modelLine.getChildren().addAll(title(messages.get(MessageKey.PROJECTS_MODEL_LOOKING)), tryAgain(true));
        } else if (choice.chosen()) {
            modelLine.getStyleClass().add("banner-ok");
            modelLine
                    .getChildren()
                    .addAll(
                            title(readyText(choice, outcome)),
                            settingsButton(
                                    MessageKey.PROJECTS_MODEL_CHANGE,
                                    MessageKey.PROJECTS_MODEL_CHANGE_TIP,
                                    "btn-ghost"));
        } else {
            modelLine.getStyleClass().add("banner-err");
            showNone();
        }
    }

    private String readyText(final ModelChoice choice, final ProbeOutcome outcome) {
        final boolean foundAtFirstLaunch = outcome instanceof ProbeOutcome.Found found
                && found.atFirstLaunch()
                && found.selection().modelId().equals(choice.modelId())
                && ProviderNames.displayName(messages, found.selection().providerId())
                        .equals(choice.provider());
        log.debug("model line: ready, found at first launch {}", foundAtFirstLaunch);
        return messages.get(
                foundAtFirstLaunch ? MessageKey.PROJECTS_MODEL_READY_FOUND : MessageKey.PROJECTS_MODEL_READY,
                choice.provider(),
                choice.modelId());
    }

    private void showNone() {
        final boolean declined = probe.outcome().get() instanceof ProbeOutcome.Declined;
        log.debug("model line: no model chosen, a program answered {}", declined);
        final Label help = new Label(
                messages.get(declined ? MessageKey.PROJECTS_MODEL_DECLINED_HELP : MessageKey.PROJECTS_MODEL_NONE_HELP));
        help.setWrapText(true);
        help.getStyleClass().add("banner-text");
        final Button other = settingsButton(
                MessageKey.PROJECTS_MODEL_OTHER_PROGRAM, MessageKey.PROJECTS_MODEL_OTHER_PROGRAM_TIP, "btn-ghost");
        other.setId("projects-empty-other");
        other.setMaxWidth(Region.USE_PREF_SIZE);
        final VBox words = new VBox(
                NONE_SPACING,
                title(messages.get(
                        declined ? MessageKey.PROJECTS_MODEL_DECLINED_TITLE : MessageKey.NAV_FOOTER_NO_MODEL)),
                help,
                other);
        words.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(words, Priority.ALWAYS);
        modelLine.getChildren().addAll(words, tryAgain(false));
    }

    private Button tryAgain(final boolean looking) {
        final Button button = new Button(messages.get(MessageKey.PROJECTS_MODEL_TRY_AGAIN));
        button.setId("projects-empty-tryagain");
        button.getStyleClass().add("btn-secondary");
        button.setDisable(looking);
        button.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, button, MessageKey.PROJECTS_MODEL_TRY_AGAIN_TIP);
        button.setOnAction(event -> {
            log.debug("try again pressed on the first-launch card");
            probe.tryAgain();
        });
        return button;
    }

    /** The line's wrapping bold words, taking the width the buttons leave. */
    private static Label title(final String text) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("banner-title");
        label.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Button settingsButton(final MessageKey caption, final MessageKey tip, final String style) {
        final Button button = new Button(messages.get(caption));
        button.getStyleClass().add(style);
        button.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, button, tip);
        button.setOnAction(event -> {
            log.debug("{} pressed: opening Settings", caption);
            navigator.navigate(ViewNames.SETTINGS);
        });
        return button;
    }
}
