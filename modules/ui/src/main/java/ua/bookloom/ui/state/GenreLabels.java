package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Genre;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The catalogue entry each genre is shown by. The genres live in {@code :api}, which knows no interface text, so the
 * interface maps each to its own entry here; the brief still stores the English name.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GenreLabels {

    private static final String PREFIX = "GENRE_";

    /**
     * The entry a genre is shown by.
     *
     * @param genre the genre; never null
     * @return the key named after the genre's constant, which every genre has (the catalogue test holds this)
     */
    public static MessageKey keyOf(final Genre genre) {
        Objects.requireNonNull(genre, "genre");
        return MessageKey.valueOf(PREFIX + genre.name());
    }
}
