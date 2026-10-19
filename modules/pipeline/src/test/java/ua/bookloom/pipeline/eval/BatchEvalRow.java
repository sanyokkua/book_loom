package ua.bookloom.pipeline.eval;

/**
 * One batch call's outcome.
 *
 * @param size how many items the batch held
 * @param idValid items whose id came back exactly once with text
 * @param tokenPass items that came back once and kept the source's tokens, in order, with every pair around words
 * @param missing items the reply left out or answered with nothing
 * @param duplicate items answered more than once
 * @param merged items suspected of carrying their neighbour as well
 * @param extra ids in the reply that the batch never held
 * @param tooShort items whose target is far shorter than the source's length band allows
 * @param leaked items whose target carries the protocol around the text: the {@code terms} object, an {@code id} entry or a code fence
 * @param outputTokens the completion tokens the provider reported, else an estimate of the reply
 * @param promptTokens the prompt tokens the provider reported, or -1 when it reported none
 * @param callFailed whether the call itself ended in an error, which fails every item
 */
record BatchEvalRow(
        int size,
        int idValid,
        int tokenPass,
        int missing,
        int duplicate,
        int merged,
        int extra,
        int tooShort,
        int leaked,
        int outputTokens,
        int promptTokens,
        boolean callFailed) {}
