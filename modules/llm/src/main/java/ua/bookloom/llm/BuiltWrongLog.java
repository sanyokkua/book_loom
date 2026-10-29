package ua.bookloom.llm;

import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;

/**
 * Writes one ERROR line per provider, model and reason for a refusal that is a BookLoom fault, so a request built wrong
 * is loud once and not on every call of a book. One instance is shared by every model the factory makes.
 */
@Slf4j
@Singleton
public final class BuiltWrongLog {

    // The server blames a request BookLoom shaped; the person cannot fix it, so the developer must see it.
    private static final Set<FailureReason> BOOKLOOM_FAULTS =
            EnumSet.of(FailureReason.OPTION_TYPE, FailureReason.GRAMMAR_TOO_LARGE, FailureReason.SCHEMA_INVALID);

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /**
     * Writes the ERROR line when the failure is a BookLoom fault this provider and model have not shown before.
     *
     * @param providerId the provider's id; may be empty for a model made without one
     * @param modelId the model's id
     * @param error the mapped failure; its reason is read from the typed details only
     */
    void noteIfFirst(final String providerId, final String modelId, final AppError error) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(error, "error");
        final Optional<FailureReason> reason = FailureReason.in(error.details());
        if (reason.isEmpty() || !BOOKLOOM_FAULTS.contains(reason.get())) {
            return;
        }
        final String token = reason.get().token();
        if (seen.add(providerId + "|" + modelId + "|" + token)) {
            log.error(
                    "Provider rejected a request BookLoom built wrong reason={} provider={} model={}",
                    token,
                    providerId,
                    modelId);
        } else {
            log.debug("BookLoom fault already logged reason={} provider={} model={}", token, providerId, modelId);
        }
    }
}
