package ua.bookloom.pipeline.run;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.context.ContextBudget;

/**
 * The context window a run sizes its calls for, re-read from the model's stored override each time a chunk starts so a
 * chunk is packed and sent for one window from its first call to its last. Used from the job thread only.
 */
@Slf4j
public final class RunWindow {

    private final @Nullable Integer detected;
    private final Supplier<@Nullable Integer> override;
    private int current;

    public RunWindow(@Nullable final Integer detected, final Supplier<@Nullable Integer> override) {
        this.detected = detected;
        this.override = override;
        this.current = ContextBudget.windowFor(detected, override.get());
        log.debug("Run window start window={} detected={}", current, detected);
    }

    /** The window latched at the last chunk entry. */
    public int current() {
        return current;
    }

    /** Latches the window for the chunk about to run, and returns it. */
    public int enterChunk() {
        final Integer stored = override.get();
        final int next = ContextBudget.windowFor(detected, stored);
        if (next != current) {
            log.info("Run window changed from={} to={} detected={} override={}", current, next, detected, stored);
        } else {
            log.debug("Run window kept window={} detected={} override={}", next, detected, stored);
        }
        current = next;
        return current;
    }
}
