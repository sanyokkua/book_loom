package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.StageStatus;

/** Whether the Providers form's connection details are open, and which place in them was asked for. FX thread only. */
@Slf4j
final class ProviderFoldState {

    private final SettingsViewModel viewModel;
    private final TechnicalDetails technical;
    private final BooleanProperty requested = new SimpleBooleanProperty(false);
    private final ObjectProperty<ProviderForm.@Nullable Spot> focusRequest = new SimpleObjectProperty<>();
    private final BooleanBinding open;

    ProviderFoldState(final SettingsViewModel viewModel, final TechnicalDetails technical) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.technical = Objects.requireNonNull(technical, "technical");
        this.open = Bindings.createBooleanBinding(
                this::shouldOpen,
                viewModel.adding(),
                requested,
                technical.property(),
                viewModel.tests().stages());
    }

    BooleanBinding open() {
        return open;
    }

    ReadOnlyObjectProperty<ProviderForm.@Nullable Spot> focusRequest() {
        return focusRequest;
    }

    void openAt(final ProviderForm.Spot spot) {
        log.debug("connection details opened at {}", spot);
        requested.set(true);
        focusRequest.set(null);
        focusRequest.set(spot);
    }

    /** Forgets an earlier request, for a form that now belongs to another provider. */
    void reset() {
        requested.set(false);
    }

    private boolean shouldOpen() {
        return viewModel.adding().get()
                || requested.get()
                || technical.isOn()
                || viewModel.tests().stages().stream().anyMatch(chip -> chip.status() == StageStatus.FAILED);
    }
}
