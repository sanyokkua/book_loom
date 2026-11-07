package ua.bookloom.api.pipeline;

import java.util.Optional;
import ua.bookloom.api.Result;

/**
 * Plans a stored project's proofread windows with no model call, so the window can show where a Continue picks up the
 * Proofread stage and count the windows already read from their stored fingerprints.
 */
public interface ProofreadProgress {

    /**
     * Where the project's Proofread stage stands.
     *
     * @param projectId the non-null id of an open project
     * @param batchSize the most focus segments one window holds, the batch size of the model the run will use; at
     *     least 1
     * @return the round with windows left and how far it is, or empty when the Translate stage still has segments or
     *     no window is left to read; an error when the project cannot be read
     */
    Result<Optional<ProofreadTally>> tally(String projectId, int batchSize);
}
