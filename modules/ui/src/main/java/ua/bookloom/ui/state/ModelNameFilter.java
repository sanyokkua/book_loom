package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.control.TextMatch;

/** The one rule every model picker narrows by: the typed text anywhere in a model's id or display name. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelNameFilter {

    /**
     * Tells whether a model survives the typed text.
     *
     * @param id the model's id
     * @param display the name shown for it; may repeat the id
     * @param typed what the person typed; blank matches everything
     * @return {@code true} if the text, ignoring case and accents, is part of the id or the display name
     */
    public static boolean matches(final String id, final String display, final String typed) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(typed, "typed");
        final String needle = TextMatch.normalize(typed.strip());
        return needle.isEmpty()
                || TextMatch.normalize(id).contains(needle)
                || TextMatch.normalize(display).contains(needle);
    }

    /**
     * Makes the test for a model id when the id is also its name.
     *
     * @param typed what the person typed; blank matches everything
     * @return a predicate over ids
     */
    public static Predicate<String> of(final String typed) {
        Objects.requireNonNull(typed, "typed");
        return id -> matches(id, id, typed);
    }

    /**
     * Keeps the items the typed text matches, in order.
     *
     * @param items what is on offer
     * @param idOf reads an item's model id
     * @param displayOf reads an item's display name
     * @param typed what the person typed; blank keeps all
     * @param <T> the item type
     * @return the matching items; never null, empty if none match
     */
    public static <T> List<T> narrow(
            final List<T> items,
            final Function<T, String> idOf,
            final Function<T, String> displayOf,
            final String typed) {
        return items.stream()
                .filter(item -> matches(idOf.apply(item), displayOf.apply(item), typed))
                .toList();
    }
}
