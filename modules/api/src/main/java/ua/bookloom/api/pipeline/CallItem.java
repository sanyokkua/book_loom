package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One item of a batch reply as the parser read it, so a row and the reply grid can show what each segment got.
 *
 * @param id the item's id in the reply, which is not the segment's id
 * @param segmentId the segment the id stands for, or null for an id the batch never held
 * @param target the text returned under the id, or null when none came back
 * @param status what the reply did with the id
 * @param problems the short reasons the item was refused; empty when it was taken
 */
public record CallItem(
        String id, @Nullable String segmentId, @Nullable String target, CallItemStatus status, List<String> problems) {

    /** Rejects a missing part and copies the problems. */
    public CallItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
    }
}
