package ua.bookloom.pipeline.batch;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The item markers of the {@link BatchFormat#MARKERS} arm: {@code ⟦s1⟧} at the start of item 1, and so on. A marker is
 * no placeholder ({@code ⟦gN⟧} is the only token grammar), so code writes and removes it and no gate ever sees one.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchMarkers {

    /** The id of a marker in any of the spellings a model writes: {@code ⟦s2⟧}, {@code ⟦S2⟧}, {@code ⟦ s2 ⟧}. */
    static final Pattern MARKER = Pattern.compile("⟦\\s*[sS]\\s*([A-Za-z0-9_.:-]+)\\s*⟧");

    private static final Pattern MARKER_AND_SPACE = Pattern.compile(MARKER.pattern() + "\\s*");

    /** The marker that starts the item with the given id. */
    public static String of(final String id) {
        return "⟦s" + id + "⟧";
    }

    /** The text without its item markers, and without the white space that directly follows each one. */
    public static String strip(final String text) {
        return MARKER_AND_SPACE.matcher(text).replaceAll("");
    }
}
