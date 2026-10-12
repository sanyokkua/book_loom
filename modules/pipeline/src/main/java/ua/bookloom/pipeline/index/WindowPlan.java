package ua.bookloom.pipeline.index;

/**
 * How many windows to cut and how wide.
 *
 * @param count the most windows to return
 * @param neighbours how many segments before and after the mention's own may be read, inside its unit
 * @param tokensPerWindow the budget a window's text is trimmed to, sentence by sentence, by {@code TokenEstimator}
 */
public record WindowPlan(int count, int neighbours, int tokensPerWindow) {}
