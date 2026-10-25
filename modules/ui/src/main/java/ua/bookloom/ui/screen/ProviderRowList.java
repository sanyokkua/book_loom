package ua.bookloom.ui.screen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Overrun;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderNames;
import ua.bookloom.ui.state.ProviderRow;
import ua.bookloom.ui.state.SettingsViewModel;

/** The rows of the provider list: one per provider, marking the open one and the one in use. */
@Slf4j
final class ProviderRowList {

    private static final String SELECTED_ROW = "list-item-selected";

    /** A provider row and the badge that marks it as the current one. */
    private record RowNodes(Node row, Label badge) {}

    private final SettingsViewModel viewModel;
    private final Messages messages;
    private final VBox list;
    private final Consumer<String> opener;
    private final Map<String, RowNodes> rows = new LinkedHashMap<>();

    ProviderRowList(
            final SettingsViewModel viewModel,
            final Messages messages,
            final VBox list,
            final Consumer<String> opener) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.list = Objects.requireNonNull(list, "list");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /** Rebuilds every row from the view model's providers. */
    void show() {
        log.debug("showing the rows of {} provider(s)", viewModel.providers().size());
        rows.clear();
        list.getChildren().clear();
        viewModel.providers().forEach(this::addRow);
        mark();
    }

    private void addRow(final ProviderRow provider) {
        log.debug("adding the row of provider '{}'", provider.id());
        final Label name = new Label(ProviderNames.displayName(messages, provider));
        name.getStyleClass().add("provider-name");
        final Label endpoint = new Label(provider.hostPort());
        endpoint.getStyleClass().add("provider-endpoint");
        final Label badge = new Label();
        badge.getStyleClass().add("chip");
        // The chip keeps its word whole and the name gives way, with the rest in a tooltip. The address is one token
        // that a shared line would cut ("127.0.0.1:114..."), so it has the row's whole width on a line of its own.
        badge.setMinWidth(Region.USE_PREF_SIZE);
        Overrun.install(name);
        Overrun.install(endpoint);
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox heading = new HBox(name, spacer, badge);
        heading.setAlignment(Pos.CENTER_LEFT);
        final VBox row = new VBox(heading, endpoint);
        row.setId("provider-row-" + provider.id());
        Tips.install(messages, row, MessageKey.SETTINGS_PROVIDERS_TITLE_TIP);
        row.getStyleClass().add("list-item");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFocusTraversable(true);
        // These lambdas capture this controller; the view model holds it only weakly, so the row keeps it reachable.
        row.setOnMouseClicked(event -> onRowClicked(event, row, provider));
        row.setOnKeyPressed(event -> onRowKey(event.getCode(), provider));
        rows.put(provider.id(), new RowNodes(row, badge));
        list.getChildren().add(row);
    }

    private void onRowClicked(final MouseEvent event, final Node row, final ProviderRow provider) {
        if (event.getButton() == MouseButton.PRIMARY) {
            log.debug("row of provider '{}' clicked", provider.id());
            row.requestFocus();
            opener.accept(provider.id());
        }
    }

    private void onRowKey(final KeyCode code, final ProviderRow provider) {
        if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
            log.debug("row of provider '{}' selected with {}", provider.id(), code);
            opener.accept(provider.id());
        }
    }

    void mark() {
        final String edited = viewModel.editedProviderId().get();
        final String current = viewModel.selectedProviderId().get();
        log.debug("marking the row of '{}' as open and '{}' as current", edited, current);
        rows.forEach((id, nodes) -> {
            final boolean isCurrent = id.equals(current);
            nodes.row().getStyleClass().remove(SELECTED_ROW);
            if (id.equals(edited)) {
                nodes.row().getStyleClass().add(SELECTED_ROW);
            }
            nodes.badge()
                    .setText(messages.get(
                            isCurrent ? MessageKey.SETTINGS_PROVIDER_CURRENT : MessageKey.SETTINGS_PROVIDER_IDLE));
            nodes.badge().getStyleClass().removeAll("chip-ok", "chip-neutral");
            nodes.badge().getStyleClass().add(isCurrent ? "chip-ok" : "chip-neutral");
        });
    }
}
