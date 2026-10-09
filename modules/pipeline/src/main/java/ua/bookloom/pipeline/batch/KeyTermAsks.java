package ua.bookloom.pipeline.batch;

import lombok.extern.slf4j.Slf4j;

/**
 * Whether a run's batch prompts still ask for key-term renderings. The field costs prompt and reply tokens in every
 * batch, and a model that ignores it (the small local model answered it in 1 batch of 40) would pay for nothing, so the
 * run stops asking once its first {@link #PROBE_BATCHES} asking batches were all answered without one; a model that
 * answers even once keeps being asked for the rest of the run.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class KeyTermAsks {

    /**
     * How many batches that asked for renderings must answer none before the run stops asking: five batches of the
     * default size is forty segments, enough that a model which uses the field has shown it and a model that ignores it
     * has wasted little.
     */
    public static final int PROBE_BATCHES = 5;

    private int askedAndSilent;
    private boolean hasAnswered;

    /** Whether the next batch's prompt should carry the key-term block. */
    public boolean isAsking() {
        return hasAnswered || askedAndSilent < PROBE_BATCHES;
    }

    /**
     * Notes how a readable batch reply treated the key-term block.
     *
     * @param wasAsked whether the batch's prompt carried the block
     * @param hasTerms whether the reply reported a rendering for any item
     */
    public void record(final boolean wasAsked, final boolean hasTerms) {
        if (!wasAsked) {
            log.trace("Key-term asks unchanged: the batch asked for none");
            return;
        }
        if (hasTerms) {
            hasAnswered = true;
        } else if (!hasAnswered) {
            askedAndSilent++;
            if (askedAndSilent == PROBE_BATCHES) {
                log.info("Batch prompts stop asking for key terms: {} asking batches answered none", PROBE_BATCHES);
            }
        }
        log.trace(
                "Key-term asks recorded hasTerms={} askedAndSilent={} hasAnswered={} asking={}",
                hasTerms,
                askedAndSilent,
                hasAnswered,
                isAsking());
    }
}
