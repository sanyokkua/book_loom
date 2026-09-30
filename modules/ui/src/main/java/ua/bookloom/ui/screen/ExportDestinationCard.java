package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.ExportViewModel;

/**
 * Save to with Browse, the replace switch and the refusal or occupied-path warning under the field.
 *
 * <p>The view model outlives this card, so it is observed through weak listeners held by the fields below; the screen
 * keeps the card alive for as long as its nodes are shown. The refusal text and the replace switch are the view
 * model's own words and state, never recomputed here.
 */
@Slf4j
final class ExportDestinationCard {

    private static final double ROW_SPACING = 8;
    private static final double FIELD_SPACING = 5;

    private final ExportViewModel viewModel;
    private final Messages messages;
    private final DestinationChooser chooser;
    private final TextField field = new TextField();
    private final ToggleSwitch replace = new ToggleSwitch();
    private final ChangeListener<String> onDestination = (observed, was, now) -> showDestination(now);
    private final ChangeListener<Boolean> onOverwrite = (observed, was, now) -> replace.setSelected(now);

    ExportDestinationCard(final ExportViewModel viewModel, final Messages messages, final DestinationChooser chooser) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
    }

    Node build() {
        field.setId("export-save-to");
        Tips.install(messages, field, MessageKey.EXPORT_SAVE_TO_TIP);
        field.setText(viewModel.destination().get());
        field.textProperty().addListener((observed, was, now) -> onTyped(now));
        viewModel.destination().addListener(new WeakChangeListener<>(onDestination));
        HBox.setHgrow(field, Priority.ALWAYS);
        final Button browse = new Button(messages.get(MessageKey.EXPORT_BROWSE));
        browse.setId("export-browse");
        Tips.install(messages, browse, MessageKey.EXPORT_BROWSE_TIP);
        browse.getStyleClass().add("btn-secondary");
        browse.setOnAction(event -> browse());
        final HBox row = new HBox(ROW_SPACING, field, browse);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(
                ROW_SPACING, BriefCards.field(messages, MessageKey.EXPORT_SAVE_TO, row), refusal(), replaceSwitch());
    }

    private Node refusal() {
        final Label refusal = new Label();
        refusal.setId("export-refusal");
        refusal.setWrapText(true);
        refusal.getStyleClass().add("status-err");
        refusal.textProperty().bind(viewModel.refusal());
        return BriefCards.shownWhile(refusal, viewModel.refusal().isNotEmpty());
    }

    private Node replaceSwitch() {
        replace.setId("export-replace");
        Tips.install(messages, replace, MessageKey.EXPORT_REPLACE_TIP);
        replace.setText(messages.get(MessageKey.EXPORT_REPLACE));
        replace.setSelected(viewModel.overwrite().get());
        replace.selectedProperty().addListener((observed, was, now) -> {
            log.debug("replace switch is now {}", now);
            viewModel.setOverwrite(now);
        });
        viewModel.overwrite().addListener(new WeakChangeListener<>(onOverwrite));
        return new VBox(FIELD_SPACING, replace);
    }

    private void onTyped(final String text) {
        if (!text.equals(viewModel.destination().get())) {
            viewModel.editDestination(text);
        }
    }

    private void showDestination(final String text) {
        if (!text.equals(field.getText())) {
            field.setText(text);
        }
    }

    private void browse() {
        final Path initial = viewModel.destinationPath().orElse(Path.of(""));
        log.debug("browse pressed, the dialog starts at {}", initial);
        chooser.choose(initial).ifPresent(viewModel::chooseDestination);
    }
}
