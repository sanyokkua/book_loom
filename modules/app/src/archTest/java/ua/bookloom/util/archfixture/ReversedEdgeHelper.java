package ua.bookloom.util.archfixture;

import ua.bookloom.pipeline.PipelineModule;

/**
 * Violation fixture for {@code dependency-direction}: a {@code :util} class depending on {@code :pipeline}. The
 * allowed-edge table gives {@code :util} exactly one outgoing edge — to {@code :api} — so this reverses the
 * graph's direction and, left unchecked, would introduce the first cycle.
 */
public final class ReversedEdgeHelper {

    public String describe(PipelineModule module) {
        return module.getClass().getSimpleName();
    }
}
