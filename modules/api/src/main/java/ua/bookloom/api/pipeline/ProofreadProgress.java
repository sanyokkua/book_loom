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

    /**
     * Whether a Continue has work for the Proofread stage's code part, which runs at every dial with no model call: a
     * renamed target the term sweep has not carried through the decided text yet, or a gender deferral whose fact has
     * arrived and that the text already agrees with.
     *
     * @param projectId the non-null id of an open project
     * @return {@code true} if a Continue would change something with no model call, {@code false} otherwise; an
     *     error when the project cannot be read. A planner with no code part has none, the default.
     */
    default Result<Boolean> codeWorkLeft(final String projectId) {
        return Result.ok(false);
    }
}
