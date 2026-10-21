package ua.bookloom.api.llm;

import java.util.List;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * Port for asking a registered provider which models it offers.
 *
 * <p>Exists so a screen can fill a model list without reaching the provider clients, which sit behind the
 * {@code :llm} module edge. Listing is not inference, so it never waits behind a running translation.
 */
public interface ModelCatalog {

    /**
     * Lists the models the provider reports.
     *
     * @param providerId the id of a registered provider; never {@code null}
     * @return the model ids in the order the provider reports them (empty when it reports none), or an error —
     *     {@link ua.bookloom.api.ErrorCode#validation} for an unregistered id (no request is sent), otherwise the discovery failure
     */
    Result<List<ModelInfo>> listModels(String providerId);

    /**
     * Lists the models an unsaved description reports; nothing is registered or stored.
     *
     * @param draft the unsaved provider description
     * @return the model ids, or an error: {@code validation} for a description the rules refuse, otherwise the
     *     discovery failure
     */
    default Result<List<ModelInfo>> listModels(ProviderConfig draft) {
        return Result.err(AppError.of(
                ErrorCode.internal, "Not supported", "This catalog cannot list a description that is not saved."));
    }
}
