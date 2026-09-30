package ua.bookloom.api.llm;

import ua.bookloom.api.Result;

/** Port for verifying a selected provider/model pair. */
public interface ProviderVerifier {

    /**
     * Runs the stages selected by the verification policy.
     *
     * @param selection the selected provider and model
     * @param policy the verification depth
     * @return the stage report, or an error if the selection cannot be verified
     */
    Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy);

    /**
     * Runs the connection stage alone, for a provider with no model chosen yet.
     *
     * @param providerId the registry id of the provider to reach
     * @return a report of one {@link VerificationStage#CONNECTION} stage carrying its elapsed time, or a
     *     {@code validation} error for an id nobody registered
     */
    Result<VerificationReport> verifyConnection(String providerId);
}
