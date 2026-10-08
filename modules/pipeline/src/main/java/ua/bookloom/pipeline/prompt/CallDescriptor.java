package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.PromptSection;

/**
 * What a call site knows about a model call besides its request, so the run can show the call as a person reads it.
 * The sections are built from the same slot values the request was rendered from, and only when a seam shows them.
 *
 * @param label the name of the prompt the call was built from ({@link PromptName#resourceBaseName()})
 * @param position the chunk the call is made in, or null when the call site does not know it
 * @param sources each segment's source as a person reads it, in the order of the call's segment ids
 * @param sections the parts of the prompt as sent, in order; called at most once, when the first attempt goes out
 */
public record CallDescriptor(
        String label, @Nullable ChunkPosition position, List<String> sources, Supplier<List<PromptSection>> sections) {

    /** Rejects a missing part and copies the sources. */
    public CallDescriptor {
        Objects.requireNonNull(label, "label");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(sections, "sections");
    }

    /**
     * A call about one segment, named by the prompt it renders.
     *
     * @param name the non-null prompt the call renders
     * @param source the non-null segment's source as a person reads it
     * @param sections the non-null parts of the prompt as sent
     * @return the descriptor, with no chunk position
     */
    public static CallDescriptor of(
            final PromptName name, final String source, final Supplier<List<PromptSection>> sections) {
        return new CallDescriptor(name.resourceBaseName(), null, List.of(source), sections);
    }
}
