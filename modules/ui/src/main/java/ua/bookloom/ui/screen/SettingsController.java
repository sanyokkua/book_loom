package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelListing;
import ua.bookloom.ui.state.ProviderRow;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StageChip;

/**
 * The settings screen: the providers list, the selected provider's card and its check.
 *
 * <p>The rows and chips are built here from the view model's lists because their number and wording depend on data;
 * everything static is in the FXML. The view model outlives this controller, so it is observed through weak listeners
 * held by the fields below, which live exactly as long as the nodes that use them.
 */
@Slf4j
public final class SettingsController {

    private static final String SELECTED_ROW = "list-item-selected";

    private static final int MODELS_SHOWN = 3;

    /**
     * A provider row and the badge that marks it as the current one.
     *
     * @param row the clickable row node
     * @param badge the "current" mark, shown only on the selected row
     */
    private record RowNodes(Node row, Label badge) {}

    private final SettingsViewModel viewModel;
    private final Messages messages;
    private final StageChipRows chipRows;
    private final Map<String, RowNodes> rows = new LinkedHashMap<>();
    private final ChangeListener<String> onSelection = (observed, was, now) -> markSelectedRow();
    private final ListChangeListener<StageChip> onStages = change -> showChips();
    private final ListChangeListener<String> onOffered = change -> showOffered();
    private final ChangeListener<String> onEntryText = (observed, was, now) -> onEntryTyped(now);
    private final ChangeListener<String> onModel = (observed, was, now) -> showModel(now);

    /**
     * Set while this controller itself changes the combo's items, editor or value, so that the events those changes
     * cause are not mistaken for a person typing or picking; FX thread only.
     */
    private boolean applying;

    @FXML
    private VBox providerList;

    @FXML
    private Label detailName;

    @FXML
    private Label detailEndpoint;

    @FXML
    private ComboBox<String> modelCombo;

    @FXML
    private Label modelListing;

    @FXML
    private Label modelNoList;

    @FXML
    private Label modelRefusal;

    @FXML
    private Label checkHint;

    @FXML
    private Label detailKind;

    @FXML
    private Label detailModels;

    @FXML
    private Button testConnection;

    @FXML
    private Button testModels;

    @FXML
    private Button testInference;

    @FXML
    private Label checkProgress;

    @FXML
    private Label checkRefusal;

    @FXML
    private Pane chips;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the state this screen shows
     * @param messages the catalogue the built rows and chips are worded from
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public SettingsController(final SettingsViewModel viewModel, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chipRows = new StageChipRows(messages);
    }

    @FXML
    void initialize() {
        log.debug(
                "building the settings screen for {} provider(s)",
                viewModel.providers().size());
        viewModel.providers().forEach(this::addRow);
        markSelectedRow();
        viewModel.selectedProviderId().addListener(new WeakChangeListener<>(onSelection));
        bindDetail();
        bindModel();
        bindTests();
        viewModel.tests().stages().addListener(new WeakListChangeListener<>(onStages));
        showChips();
        viewModel.refreshModels();
    }

    private void bindDetail() {
        detailName
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> ProviderNames.displayName(
                                messages, viewModel.selectedProviderId().get()),
                        viewModel.selectedProviderId()));
        detailKind
                .textProperty()
                .bind(Bindings.createStringBinding(this::selectedKind, viewModel.selectedProviderId()));
        detailModels
                .textProperty()
                .bind(Bindings.createStringBinding(
                        this::modelsFound,
                        viewModel.selectedProviderId(),
                        viewModel.modelListing().offered()));
        detailEndpoint
                .textProperty()
                .bind(Bindings.createStringBinding(this::selectedEndpoint, viewModel.selectedProviderId()));
    }

    /**
     * The combo keeps its own copy of the offered ids and its own text, and only listens to the view model: a combo
     * whose items are the singleton's list also has them rewritten by its skin, and replacing the items would blank
     * the entry and lose what the person typed.
     */
    private void bindModel() {
        final ModelListing listing = viewModel.modelListing();
        modelCombo.getItems().setAll(listing.offered());
        applyText(viewModel.model().get());
        modelCombo.getEditor().textProperty().addListener(onEntryText);
        viewModel.model().addListener(new WeakChangeListener<>(onModel));
        listing.offered().addListener(new WeakListChangeListener<>(onOffered));
        modelCombo.setOnAction(event -> onEntryCommitted());
        show(modelListing, listing.listing());
        show(modelNoList, listing.noListObtained());
        modelRefusal.textProperty().bind(listing.refusal());
        modelRefusal
                .visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !listing.refusal().get().isEmpty(), listing.refusal()));
        modelRefusal.managedProperty().bind(modelRefusal.visibleProperty());
    }

    private static void show(final Label note, final ObservableBooleanValue shown) {
        note.visibleProperty().bind(shown);
        note.managedProperty().bind(note.visibleProperty());
    }

    private void onEntryTyped(final String text) {
        if (applying) {
            return;
        }
        log.trace("model entry now holds '{}'", text);
        viewModel.setModelText(text);
        applyValue(text);
    }

    private void onEntryCommitted() {
        if (!applying) {
            log.debug("model entry committed");
            viewModel.commitModel();
        }
    }

    private void showModel(final String model) {
        if (!model.equals(modelCombo.getEditor().getText())) {
            log.debug("model text of {} character(s) shown in the entry", model.length());
            applyText(model);
        }
    }

    private void showOffered() {
        log.debug(
                "showing {} offered model(s)",
                viewModel.modelListing().offered().size());
        final String typed = modelCombo.getEditor().getText();
        applying = true;
        try {
            modelCombo.getItems().setAll(List.copyOf(viewModel.modelListing().offered()));
        } finally {
            applying = false;
        }
        applyText(typed);
    }

    private void applyText(final String text) {
        applying = true;
        try {
            modelCombo.getEditor().setText(text);
            modelCombo.setValue(text.isEmpty() ? null : text);
        } finally {
            applying = false;
        }
    }

    /**
     * Keeps the combo's value equal to what was typed: an editable combo whose value is not one of its items would
     * otherwise have its skin reset the value, and with it the entry, when the items change.
     */
    private void applyValue(final String text) {
        applying = true;
        try {
            modelCombo.setValue(text.isEmpty() ? null : text);
        } finally {
            applying = false;
        }
    }

    private void bindTests() {
        bindTest(testConnection, ProviderTest.CONNECTION);
        bindTest(testModels, ProviderTest.MODELS);
        bindTest(testInference, ProviderTest.INFERENCE);
        checkProgress.visibleProperty().bind(viewModel.tests().checking());
        checkProgress.managedProperty().bind(checkProgress.visibleProperty());
        checkHint
                .visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> viewModel.model().get().isBlank(), viewModel.model()));
        checkHint.managedProperty().bind(checkHint.visibleProperty());
        checkRefusal.textProperty().bind(viewModel.tests().checkRefusal());
        checkRefusal
                .visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !viewModel.tests().checkRefusal().get().isEmpty(),
                        viewModel.tests().checkRefusal()));
        checkRefusal.managedProperty().bind(checkRefusal.visibleProperty());
    }

    private void bindTest(final Button button, final ProviderTest test) {
        button.disableProperty().bind(Bindings.not(viewModel.tests().available(test)));
        button.setOnAction(event -> viewModel.tests().run(test));
    }

    private Optional<ProviderRow> selectedRow() {
        final String selected = viewModel.selectedProviderId().get();
        return viewModel.providers().stream()
                .filter(row -> row.id().equals(selected))
                .findFirst();
    }

    private String selectedEndpoint() {
        return selectedRow().map(ProviderRow::endpoint).orElse("");
    }

    private String selectedKind() {
        return selectedRow()
                .map(row -> messages.get(
                        row.kind() == ProviderKind.OLLAMA
                                ? MessageKey.SETTINGS_KIND_OLLAMA
                                : MessageKey.SETTINGS_KIND_OPENAI))
                .orElse("");
    }

    /** The count and the first names of the last listing, or a plain statement that none was made. */
    private String modelsFound() {
        final List<String> offered = List.copyOf(viewModel.modelListing().offered());
        return offered.isEmpty()
                ? messages.get(MessageKey.SETTINGS_MODELS_NONE)
                : messages.get(
                        MessageKey.SETTINGS_MODELS_FOUND,
                        offered.size(),
                        String.join(", ", offered.subList(0, Math.min(MODELS_SHOWN, offered.size()))));
    }

    private void addRow(final ProviderRow provider) {
        log.debug("adding the row of provider '{}'", provider.id());
        final Label name = new Label(ProviderNames.displayName(messages, provider.id()));
        name.getStyleClass().add("provider-name");
        final Label endpoint = new Label(provider.hostPort());
        endpoint.getStyleClass().add("provider-endpoint");
        final Label badge = new Label();
        badge.getStyleClass().add("chip");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox row = new HBox(new VBox(name, endpoint), spacer, badge);
        row.setId("provider-row-" + provider.id());
        row.getStyleClass().add("list-item");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFocusTraversable(true);
        // These lambdas capture this controller; the view model holds it only weakly, so the row keeps it reachable.
        row.setOnMouseClicked(event -> onRowClicked(event, row, provider));
        row.setOnKeyPressed(event -> onRowKey(event.getCode(), provider));
        rows.put(provider.id(), new RowNodes(row, badge));
        providerList.getChildren().add(row);
    }

    private void onRowClicked(final MouseEvent event, final Node row, final ProviderRow provider) {
        if (event.getButton() == MouseButton.PRIMARY) {
            log.debug("row of provider '{}' clicked", provider.id());
            row.requestFocus();
            viewModel.selectProvider(provider.id());
        }
    }

    private void onRowKey(final KeyCode code, final ProviderRow provider) {
        if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
            log.debug("row of provider '{}' selected with {}", provider.id(), code);
            viewModel.selectProvider(provider.id());
        }
    }

    private void markSelectedRow() {
        final String selected = viewModel.selectedProviderId().get();
        log.debug("marking the row of '{}' as selected", selected);
        rows.forEach((id, nodes) -> {
            final boolean isSelected = id.equals(selected);
            nodes.row().getStyleClass().remove(SELECTED_ROW);
            if (isSelected) {
                nodes.row().getStyleClass().add(SELECTED_ROW);
            }
            nodes.badge()
                    .setText(messages.get(
                            isSelected ? MessageKey.SETTINGS_PROVIDER_CURRENT : MessageKey.SETTINGS_PROVIDER_IDLE));
            nodes.badge().getStyleClass().removeAll("chip-ok", "chip-neutral");
            nodes.badge().getStyleClass().add(isSelected ? "chip-ok" : "chip-neutral");
        });
    }

    private void showChips() {
        final List<Node> built = viewModel.tests().stages().stream()
                .map(chipRows::rowOf)
                .map(Node.class::cast)
                .toList();
        log.debug("showing {} stage chip(s)", built.size());
        chips.getChildren().setAll(built);
    }
}
