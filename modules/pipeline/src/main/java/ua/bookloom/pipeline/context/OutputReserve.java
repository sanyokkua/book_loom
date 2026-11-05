package ua.bookloom.pipeline.context;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ReasoningControl;

/**
 * The output a translate-family call reserves for its reply (design D8, "Output reserve"): what its items' sources
 * need in the pair's ratio plus each item's id and wrapper, so a model that loops is stopped and a full batch is not.
 * A model that always reasons spends much of its output on thinking, so it is given far more room: the window fit keeps
 * that room, and the model's client adds it to the answer's cap when it sends the call (design D20). The reply never
 * gets more than half of the run's window, which the prompt shares.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutputReserve {

    /** Output tokens per source token for a pair whose file states none; measured on en→uk. */
    public static final double DEFAULT_OUTPUT_RATIO = 1.5;

    /** What one item's id and JSON wrapper cost in the reply. */
    public static final int OUTPUT_PER_ITEM = 16;

    /** The least a model that always reasons is given. */
    public static final int REASONING_RESERVE_MIN = 2048;

    private static final int REASONING_PER_SOURCE_TOKEN = 4;
    private static final int REASONING_PER_ITEM = 35;
    private static final int WINDOW_SHARE_DIVISOR = 2;

    /**
     * The reserve of one call.
     *
     * @param sourceTokens the estimated tokens of the focus items' masked sources, in the source language; zero or more
     * @param items how many items the call works on; zero or more
     * @param ratio the pair's output tokens per source token; positive
     * @param kind the model's reasoning switch; ALWAYS and UNSWITCHABLE get the reasoning reserve, UNKNOWN is read as a
     *     model that does not think
     * @param window the run's window in tokens; positive
     * @return the reserve in tokens, at most half the window
     */
    public static int of(
            final int sourceTokens,
            final int items,
            final double ratio,
            final ReasoningControl.Kind kind,
            final int window) {
        Objects.requireNonNull(kind, "kind");
        if (sourceTokens < 0 || items < 0 || ratio <= 0 || window <= 0) {
            throw new IllegalArgumentException("invalid output reserve: sourceTokens=" + sourceTokens + " items="
                    + items + " ratio=" + ratio + " window=" + window);
        }
        final int wanted =
                switch (kind) {
                    case ALWAYS, UNSWITCHABLE ->
                        Math.max(
                                REASONING_RESERVE_MIN,
                                REASONING_PER_SOURCE_TOKEN * sourceTokens + REASONING_PER_ITEM * items);
                    case NONE, BOOLEAN, LEVELS, UNKNOWN ->
                        (int) Math.ceil(ratio * sourceTokens + OUTPUT_PER_ITEM * items);
                };
        final int reserve = Math.min(wanted, window / WINDOW_SHARE_DIVISOR);
        log.debug(
                "Output reserve sourceTokens={} items={} ratio={} reasoning={} window={} wanted={} reserve={}",
                sourceTokens,
                items,
                ratio,
                kind,
                window,
                wanted,
                reserve);
        return reserve;
    }
}
