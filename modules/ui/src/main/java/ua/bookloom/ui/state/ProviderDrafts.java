package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;

/** Builds the drafts the Providers form starts from, out of what the view model holds. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@Slf4j
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

    /**
     * What a test may be run against: the draft as a description, under the open provider's id or, while adding, under
     * {@link TestSubject#DRAFT_ID}.
     */
    static Optional<TestSubject> subjectOf(
            final SettingsViewModel viewModel, final ProviderDraft draft, final ProviderDraft baseline) {
        final boolean adding = viewModel.adding().get();
        final String id =
                adding ? TestSubject.DRAFT_ID : viewModel.editedProviderId().get();
        final ProviderConfig config = id.isEmpty() ? null : draft.toConfig(id).data();
        return config == null
                ? Optional.empty()
                : Optional.of(new TestSubject(
                        config, draft.model(), draft.name().strip(), !adding && matchesSaved(draft, baseline)));
    }

    // A speed is kept only for a provider as saved; connection values typed but not saved describe another server.
    private static boolean matchesSaved(final ProviderDraft draft, final ProviderDraft baseline) {
        // A name is only a label: renaming must neither stop the speed being kept nor clear the results.
        return draft.withModel(baseline.model()).withName(baseline.name()).equals(baseline);
    }

    /**
     * What the form finds in the draft, and also whether another provider already shows the typed name, ignoring case
     * and outer spaces — two providers of one name could not be told apart in the list or the title bar.
     */
    static List<ProviderDraft.FormFinding> findings(final SettingsViewModel viewModel, final ProviderDraft draft) {
        final String typed = draft.name().strip();
        final String own =
                viewModel.adding().get() ? "" : viewModel.editedProviderId().get();
        final boolean used = !typed.isEmpty()
                && viewModel.providers().stream()
                        .filter(row -> !row.id().equals(own))
                        .map(row -> row.displayName().isBlank() ? row.id() : row.displayName())
                        .anyMatch(name -> name.strip().equalsIgnoreCase(typed));
        log.debug("provider name '{}' used by another provider: {}", typed, used);
        final List<ProviderDraft.FormFinding> found = new ArrayList<>(draft.findings());
        if (used) {
            found.add(0, ProviderFindings.blocking(MessageKey.SETTINGS_ERROR_NAME_USED, ProviderDraft.Field.NAME));
        }
        return List.copyOf(found);
    }

    /** The id a save writes: a new provider gets one made from its name, an open one keeps its own. */
    static String idToSave(final SettingsViewModel viewModel, final ProviderDraft typed, final boolean adding) {
        final List<String> taken =
                viewModel.providers().stream().map(ProviderRow::id).toList();
        return adding
                ? ProviderIds.newId(typed.name(), taken)
                : viewModel.editedProviderId().get();
    }

    /** Whether Use this one may be pressed: a saved provider is open and at most its model differs from what is saved. */
    static boolean canUse(final SettingsViewModel viewModel, final ProviderDraft draft, final ProviderDraft baseline) {
        return !viewModel.adding().get()
                && !viewModel.editedProviderId().get().isEmpty()
                && matchesSaved(draft, baseline);
    }

    /**
     * Hands the view model what a test may be run against, and whether the draft is what stops one. A form the
     * injector builds off the FX thread must not touch what the screen observes from there, so the publication is
     * then made later, from whatever the draft is by then.
     */
    static void publishSubject(
            final SettingsViewModel viewModel,
            final Supplier<ProviderDraft> draft,
            final Supplier<ProviderDraft> baseline) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> publishSubject(viewModel, draft, baseline));
            return;
        }
        final boolean open =
                viewModel.adding().get() || !viewModel.editedProviderId().get().isEmpty();
        final Optional<TestSubject> subject = subjectOf(viewModel, draft.get(), baseline.get());
        viewModel.publishTestSubject(subject, open && subject.isEmpty());
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
