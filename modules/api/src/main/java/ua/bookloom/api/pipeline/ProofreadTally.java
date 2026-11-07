package ua.bookloom.api.pipeline;

/**
 * Where a stored project's Proofread stage stands, read from the stored verdicts with no model call, so a reopened
 * project can say "Continues with Proofread at window 41 of 120." and count the windows already read.
 *
 * @param round the round with windows left, 1 or 2
 * @param windows the windows the round holds; at least 1
 * @param read the windows of the round whose focus already holds a verdict of it, from zero to {@code windows} less
 *     one
 * @param next the window a Continue reads first, counted from 1
 */
public record ProofreadTally(int round, int windows, int read, int next) {

    /** Rejects counts that cannot hold together. */
    public ProofreadTally {
        if (round < 1 || round > ProofreadRoundStarted.WHOLE_BOOK) {
            throw new IllegalArgumentException("round must be 1 or 2: " + round);
        }
        if (windows < 1 || read < 0 || read >= windows || next < 1 || next > windows) {
            throw new IllegalArgumentException("read " + read + " next " + next + " of " + windows);
        }
    }
}
