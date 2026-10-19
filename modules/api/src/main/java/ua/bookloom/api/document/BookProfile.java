package ua.bookloom.api.document;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A book's profile — its title, author, cover, structure tree and statistics — computed by
 * {@link BookInspector#profile} from an already-opened {@link Document}.
 *
 * @param title the book's title, or {@code null} when it declares none
 * @param author the book's author, or {@code null} when it declares none
 * @param cover the book's cover image, or {@code null} when it has none
 * @param structure the book's structure tree, in navigation order, defensively copied and unmodifiable
 * @param stats the book's computed statistics
 * @param resourceIds the ids of every resource (image, font, and similar) the book carries, defensively copied and
 *     unmodifiable
 */
public record BookProfile(
        @Nullable String title,
        @Nullable String author,
        @Nullable CoverImage cover,
        List<StructureNode> structure,
        BookStats stats,
        Set<String> resourceIds) {

    /**
     * Validates the non-nullable components and defensively copies {@code structure}/{@code resourceIds} so a
     * caller-held mutable collection cannot corrupt this record after construction.
     */
    public BookProfile {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(resourceIds, "resourceIds");
        structure = List.copyOf(structure);
        resourceIds = Set.copyOf(resourceIds);
    }
}
