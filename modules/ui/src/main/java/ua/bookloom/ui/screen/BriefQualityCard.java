package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The Quality vs speed card: the dial, a hint naming what the chosen position turns on, and the model the run will use
 * with a link to the settings where it is changed. The row carries no readiness badge, because no verification result
 * is kept between checks.
 */
@Slf4j
final class BriefQualityCard {

    private static final double ROW_SPACING = 8;
    private static final Map<QualityDial, MessageKey> HINTS = Map.of(
            QualityDial.FAST, MessageKey.BRIEF_QUALITY_HINT_FAST,
            QualityDial.BALANCED, MessageKey.BRIEF_QUALITY_HINT_BALANCED,
            QualityDial.MAX, MessageKey.BRIEF_QUALITY_HINT_MAX);

    private final Messages messages;
    private final SettingsViewModel settings;
    private final BriefChoice<QualityDial> dial;
    private final Label hint = new Label();
    private final Label model = new Label();
    private final Node node;
    // The settings are held by the view model, which outlives this card; the listeners reach it through weak
    // references.
    private final ChangeListener<Object> onSettings = (observed, was, now) -> showModel();

    BriefQualityCard(
            final BookBriefViewModel viewModel,
            final SettingsViewModel settings,
            final Messages messages,
            final Navigator navigator) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dial = new BriefChoice<>(
                "brief-quality",
                messages,
                List.of(
                        new BriefChoice.Option<>(QualityDial.FAST, MessageKey.BRIEF_QUALITY_FAST),
                        new BriefChoice.Option<>(QualityDial.BALANCED, MessageKey.BRIEF_QUALITY_BALANCED),
                        new BriefChoice.Option<>(QualityDial.MAX, MessageKey.BRIEF_QUALITY_MAX)),
                viewModel::setDial);
        hint.setId("brief-quality-hint");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        model.setId("brief-model");
        settings.model().addListener(new WeakChangeListener<>(onSettings));
        settings.selectedProviderId().addListener(new WeakChangeListener<>(onSettings));
        showModel();
        this.node = BriefCards.card(
                "brief-quality-card", messages, MessageKey.BRIEF_CARD_QUALITY, dial.node(), hint, modelRow(navigator));
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        dial.show(brief.dial());
        hint.setText(messages.get(Objects.requireNonNull(HINTS.get(brief.dial()), "a hint for every dial position")));
    }

    private Node modelRow(final Navigator navigator) {
        final Label name = new Label(messages.get(MessageKey.BRIEF_MODEL_LABEL));
        name.getStyleClass().add("muted");
        final Button change = new Button(messages.get(MessageKey.BRIEF_MODEL_CHANGE));
        change.setId("brief-model-change");
        change.getStyleClass().add("btn-ghost");
        change.setOnAction(event -> {
            log.debug("the model row's change link pressed: opening the settings");
            navigator.navigate(ViewNames.SETTINGS);
        });
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox row = new HBox(ROW_SPACING, name, model, spacer, change);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void showModel() {
        final Optional<ModelSelection> chosen = settings.selection();
        model.setText(chosen.map(selection -> messages.get(
                        MessageKey.BRIEF_MODEL_VALUE,
                        selection.modelId(),
                        ProviderNames.displayName(messages, selection.providerId())))
                .orElseGet(() -> messages.get(MessageKey.BRIEF_MODEL_NONE)));
    }
}
