package ua.bookloom.api.pipeline;

/**
 * A round of the Proofread stage started: round 1 reads the marked and doubted windows, round 2 (Max only) every story
 * paragraph again.
 *
 * @param round the round, 1 or 2
 * @param rounds how many rounds the run's quality reads, 1 or 2; at least {@code round}
 * @param windows the windows the round holds
 * @param toRead the windows not already read with the fingerprint they have now, from zero to {@code windows}
 */
public record ProofreadRoundStarted(int round, int rounds, int windows, int toRead) implements JobEvent {

    /** The round of Max's whole-book reading. */
    public static final int WHOLE_BOOK = 2;

    /** Rejects a round or a count that cannot be. */
    public ProofreadRoundStarted {
        if (round < 1 || rounds < round || rounds > WHOLE_BOOK) {
            throw new IllegalArgumentException("round " + round + " of " + rounds);
        }
        if (windows < 0 || toRead < 0 || toRead > windows) {
            throw new IllegalArgumentException("toRead " + toRead + " of " + windows);
        }
    }

    /** {@return {@code true} if this is Max's whole-book round, {@code false} for the marked windows} */
    public boolean isWholeBook() {
        return round == WHOLE_BOOK;
    }
}
