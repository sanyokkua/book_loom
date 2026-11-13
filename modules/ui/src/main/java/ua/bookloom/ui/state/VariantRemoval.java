package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelVariant;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;

/**
 * Removes a typed {@code @variant} from an LM Studio model id once the server's list has been read (a passing models
 * check) or the provider is saved, and says so once: LM Studio would load a second copy of the model for such an id.
 * The split is {@link ModelVariant}'s, the same the provider client applies before it sends an id. FX thread only.
 */
@Slf4j
final class VariantRemoval {

    private final SettingsViewModel viewModel;
    private final ProviderForm form;
    private final ReadOnlyStringWrapper removed = new ReadOnlyStringWrapper("");

    // The id this class wrote into the entry; any other text is the person's typing and ends the notice.
    private String strippedTo = "";

    VariantRemoval(final SettingsViewModel viewModel, final ProviderForm form) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.form = Objects.requireNonNull(form, "form");
        viewModel.tests().lastReport().addListener((observed, was, now) -> onReport(now));
        viewModel.editedProviderId().addListener((observed, was, now) -> clear("another provider"));
        viewModel.adding().addListener((observed, was, now) -> clear("adding changed"));
        viewModel.draftModel().addListener((observed, was, now) -> onTyped(now));
        form.saves().addListener((observed, was, now) -> strip("saved"));
    }

    /** The variant removed from the open provider's model id, without its {@code @}; empty when none was. */
    ReadOnlyStringProperty removed() {
        return removed.getReadOnlyProperty();
    }

    private void onReport(final @Nullable VerificationReport report) {
        final boolean listed = report != null
                && report.stages().stream()
                        .anyMatch(stage ->
                                stage.stage() == VerificationStage.MODELS && stage.status() == StageStatus.PASSED);
        log.debug("variant check after a test: models stage passed {}", listed);
        if (listed) {
            strip("models listed");
        }
    }

    private void strip(final String why) {
        final ServerEngine engine = form.draft().get().engine().effective();
        final ModelVariant split = ModelVariant.of(viewModel.draftModel().get().strip());
        log.debug("variant check ({}): engine {}, variant found {}", why, engine, split.hasVariant());
        if (engine != ServerEngine.LM_STUDIO || !split.hasVariant()) {
            return;
        }
        final String variant = Objects.requireNonNull(split.variant(), "variant");
        log.info("LM Studio model variant @{} removed from the typed id; {} is kept", variant, split.id());
        strippedTo = split.id();
        viewModel.setModelText(split.id());
        removed.set(variant);
    }

    private void onTyped(final String text) {
        if (!removed.get().isEmpty() && !text.strip().equals(strippedTo)) {
            clear("the model id was typed again");
        }
    }

    private void clear(final String why) {
        if (!removed.get().isEmpty()) {
            log.debug("variant notice cleared: {}", why);
        }
        strippedTo = "";
        removed.set("");
    }
}
