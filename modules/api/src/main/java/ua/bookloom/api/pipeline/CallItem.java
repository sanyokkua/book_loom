package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    /**
     * What a call about no segment asked, before the model answers: the line a person reads, with no answer yet.
     *
     * @param line what was asked about, as a person reads it
     * @return an item that holds the line and no target
     */
    public static CallItem asked(final String line) {
        return new CallItem(line, null, null, CallItemStatus.OK, List.of());
    }

    /**
     * What a call about no segment asked and what its reply gave for it, once parsed.
     *
     * @param line what was asked about, as a person reads it
     * @param answer the parsed answer for it, as a person reads it
     * @return an item that holds both
     */
    public static CallItem answered(final String line, final String answer) {
        return new CallItem(line, null, Objects.requireNonNull(answer, "answer"), CallItemStatus.OK, List.of());
    }

    /**
     * Words parsed enum values for a screen: lower case, a space for an underscore, a comma between.
     *
     * @param values the parsed values, such as a gender and a type
     * @return them as one short phrase
     */
    public static String words(final Enum<?>... values) {
        return Stream.of(values)
                .map(value -> value.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(Collectors.joining(", "));
    }
}
