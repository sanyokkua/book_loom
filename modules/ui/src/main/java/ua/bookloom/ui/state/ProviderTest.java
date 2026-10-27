package ua.bookloom.ui.state;

import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationStage;

/** The three questions a person can ask of the selected provider, each cumulative over the ones before it. */
public enum ProviderTest {
    /** Asks whether the server answers at all; needs no model, so it can be asked before one is chosen. */
    CONNECTION(VerificationPolicy.CONNECTION, VerificationStage.CONNECTION, false),

    /**
     * Also asks whether the server's model list can be read and, once a model is chosen, whether it holds it; needs no
     * model, so a new provider's models can be listed first.
     */
    MODELS(VerificationPolicy.CONNECTION_AND_MODELS, VerificationStage.MODELS, false),

    /** Also asks whether the chosen model returns a usable answer to a short request. */
    INFERENCE(VerificationPolicy.FULL, VerificationStage.INFERENCE, true);

    private final VerificationPolicy policy;
    private final VerificationStage lastStage;
    private final boolean needsModel;

    ProviderTest(final VerificationPolicy policy, final VerificationStage lastStage, final boolean needsModel) {
        this.policy = policy;
        this.lastStage = lastStage;
        this.needsModel = needsModel;
    }

    /**
     * How deep the verifier goes for this test.
     *
     * @return the policy the verifier is asked with
     */
    public VerificationPolicy policy() {
        return policy;
    }

    /**
     * The last stage this test reports on; every stage before it is reported too.
     *
     * @return the stage that ends this test's findings
     */
    public VerificationStage lastStage() {
        return lastStage;
    }

    /**
     * Whether the test cannot run until a model is chosen.
     *
     * @return {@code true} only for the inference test
     */
    public boolean needsModel() {
        return needsModel;
    }
}
