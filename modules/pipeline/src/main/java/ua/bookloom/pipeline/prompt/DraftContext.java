package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;

/** Context already translated in the current document section for one draft request. */
public record DraftContext(List<String> precedingTargets) {

    /** Rejects null context entries and makes the context immutable at the prompt boundary. */
    public DraftContext {
        precedingTargets = List.copyOf(Objects.requireNonNull(precedingTargets, "precedingTargets"));
    }

    /** Returns the empty context used at the start of a section. */
    public static DraftContext empty() {
        return new DraftContext(List.of());
    }
}
