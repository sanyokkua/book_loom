package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderRow;
import ua.bookloom.ui.state.SettingsViewModel;

/** The words of the selected provider's detail card, read from the settings on each call. */
final class ProviderDetailText {

    private static final int MODELS_SHOWN = 3;

    private final SettingsViewModel viewModel;
    private final Messages messages;

    ProviderDetailText(final SettingsViewModel viewModel, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    String endpoint() {
        return selectedRow().map(ProviderRow::endpoint).orElse("");
    }

    String kind() {
        return selectedRow()
                .map(row -> messages.get(
                        row.kind() == ProviderKind.OLLAMA
                                ? MessageKey.SETTINGS_KIND_OLLAMA
                                : MessageKey.SETTINGS_KIND_OPENAI))
                .orElse("");
    }

    /** The count and the first names of the last listing, or a plain statement that none was made. */
    String modelsFound() {
        final List<String> offered = List.copyOf(viewModel.modelListing().offered());
        return offered.isEmpty()
                ? messages.get(MessageKey.SETTINGS_MODELS_NONE)
                : messages.get(
                        MessageKey.SETTINGS_MODELS_FOUND,
                        offered.size(),
                        String.join(", ", offered.subList(0, Math.min(MODELS_SHOWN, offered.size()))));
    }

    private Optional<ProviderRow> selectedRow() {
        final String selected = viewModel.selectedProviderId().get();
        return viewModel.providers().stream()
                .filter(row -> row.id().equals(selected))
                .findFirst();
    }
}
