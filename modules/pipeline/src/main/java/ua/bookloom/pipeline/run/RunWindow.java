package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ContextPolicy;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.pipeline.context.ModelWindows;

/**
 * The context window a run sizes its calls for. What the server said about the window is read once, at Start; the
 * person's window for the model is read again each time a chunk starts, so a chunk is packed and sent for one window
 * from its first call to its last, and a window saved meanwhile applies from the next chunk. Used from the job thread
 * only.
 */
@Slf4j
public final class RunWindow {

    private final ChatModel model;
    private final ContextPolicy policy;
    private int current;

    /**
     * Latches the window the run starts with.
     *
     * @param model the run's model, whose profile holds what the server said about its window at Start; never null
     * @param detected the context length read at Start, or null; it counts only where the profile knows none
     */
    public RunWindow(final ChatModel model, @Nullable final Integer detected) {
        this.model = Objects.requireNonNull(model, "model");
        this.policy = ModelWindows.policyOf(model, detected);
        this.current = ModelWindow.of(policy, model.contextWindowOverride());
        log.debug(
                "Run window start window={} detected={} loaded={} hardCap={}",
                current,
                policy.detected(),
                policy.loaded(),
                policy.hardCap());
    }

    /** The window latched at the last chunk entry. */
    public int current() {
        return current;
    }

    /** Latches the window for the chunk about to run, and returns it. */
    public int enterChunk() {
        final Integer override = model.contextWindowOverride();
        final int next = ModelWindow.of(policy, override);
        if (next != current) {
            log.info("Run window changed from={} to={} override={}", current, next, override);
        } else {
            log.debug("Run window kept window={} override={}", next, override);
        }
        current = next;
        return current;
    }
}
