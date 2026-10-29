package ua.bookloom.llm.verify;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.engine.CheckedModelId;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * The models stage where the server answers any id: the same check a chat call makes against the server's own list, so
 * the Test and a run agree on what "listed" means. An id the list lacks fails the stage with {@code modelNotFound}
 * naming it; a removed LM Studio variant is said once in the stage's note.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IdCheckStage {

    /**
     * Checks the id against the server's list.
     *
     * @return the stage's verdict, or empty when the list could not be read and the usual listing decides
     */
    static Optional<ModelsProbe> probe(final ProviderClient client, final String providerId, final String modelId) {
        final Result<CheckedModelId> result = client.recheckModelId(modelId);
        if (result.isErr()) {
            final AppError error = Objects.requireNonNull(result.error(), "error");
            log.debug("Provider verification models stage id not listed provider={} model={}", providerId, modelId);
            return Optional.of(new ModelsProbe(StageOutcomes.failed(VerificationStage.MODELS, error), List.of()));
        }
        final CheckedModelId checked = Objects.requireNonNull(result.data(), "checked");
        log.debug(
                "Provider verification models stage id check provider={} model={} verdict={} variant={}",
                providerId,
                modelId,
                checked.verdict(),
                checked.removedVariant());
        if (checked.verdict() != CheckedModelId.Verdict.LISTED) {
            return Optional.empty();
        }
        final StageOutcome passed = StageOutcomes.passed(VerificationStage.MODELS, variantNote(checked));
        return Optional.of(new ModelsProbe(
                StageOutcomes.withCount(passed, checked.listedIds().size()), checked.listedIds()));
    }

    // English for now; the Providers form words the removal in the display language (task 5.8).
    private static @Nullable String variantNote(final CheckedModelId checked) {
        return checked.removedVariant() == null
                ? null
                : "The variant @" + checked.removedVariant() + " was removed; " + checked.sentId() + " is used.";
    }
}
