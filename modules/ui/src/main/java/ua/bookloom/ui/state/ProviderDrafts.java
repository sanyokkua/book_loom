package ua.bookloom.ui.state;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Builds the drafts the Providers form starts from, out of what the view model holds. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProviderDrafts {

    /** The blank draft while adding, else the open provider's stored values with the model typed for it. */
    static Optional<ProviderDraft> open(final SettingsViewModel viewModel) {
        if (viewModel.adding().get()) {
            return Optional.of(ProviderDraft.blank(ProviderKind.OLLAMA));
        }
        final String id = viewModel.editedProviderId().get();
        return viewModel
                .configOf(id)
                .map(config -> loaded(viewModel, config, viewModel.draftModel().get()));
    }

    /** What the registry now holds for a provider that was just saved, or {@code fallback} if it is gone. */
    static ProviderDraft stored(
            final SettingsViewModel viewModel, final String id, final String model, final ProviderDraft fallback) {
        return viewModel
                .configOf(id)
                .map(config -> loaded(viewModel, config, model))
                .orElse(fallback);
    }

    private static ProviderDraft loaded(
            final SettingsViewModel viewModel, final ProviderConfig config, final String model) {
        final ProviderDraft loaded = ProviderDraft.of(config, model);
        return config.displayName() == null ? loaded.withName(viewModel.providerName(config.id())) : loaded;
    }
}
