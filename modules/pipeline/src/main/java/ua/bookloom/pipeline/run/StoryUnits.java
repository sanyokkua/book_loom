package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;

/**
 * The units a name scan may read: those the book places in its body, not its front matter (title and copyright
 * pages), its back matter (acknowledgements, "also by") or the auxiliary unit.
 *
 * @param ids the ids of the story units
 */
public record StoryUnits(Set<String> ids) {

    /** Copies {@code ids} so the record cannot change after it is built. */
    public StoryUnits {
        ids = Set.copyOf(Objects.requireNonNull(ids, "ids"));
    }

    static StoryUnits of(final List<Unit> bodyUnits) {
        return new StoryUnits(bodyUnits.stream()
                .filter(unit -> unit.role() == UnitRole.BODY)
                .map(Unit::id)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Reports whether a segment is story text.
     *
     * @param segment the segment asked about; never null
     * @return {@code true} if its unit is a story unit, {@code false} otherwise
     */
    public boolean holds(final Segment segment) {
        return ids.contains(Objects.requireNonNull(segment, "segment").unit());
    }
}
