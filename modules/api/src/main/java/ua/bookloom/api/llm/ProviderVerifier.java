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
}
