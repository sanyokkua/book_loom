package ua.bookloom.ui.theme;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The monospaced face the activity log is set in: the first sturdy one the system has. The logical "Monospaced" face
 * is the thin Courier New on macOS and Windows, which reads as grey at the log's 13px. A stylesheet cannot list
 * fallbacks for a face, so the shell root carries the chosen face's style class and the stylesheet names the face
 * under it; with none of these installed the log keeps the logical face.
 */
public enum MonoFont {
    /** macOS. */
    MENLO("Menlo", "mono-menlo"),
    /** Windows. */
    CONSOLAS("Consolas", "mono-consolas"),
    /** Windows 11 and recent terminals. */
    CASCADIA_MONO("Cascadia Mono", "mono-cascadia"),
    /** Most Linux desktops. */
    DEJAVU_SANS_MONO("DejaVu Sans Mono", "mono-dejavu"),
    /** Red Hat and Fedora desktops. */
    LIBERATION_MONO("Liberation Mono", "mono-liberation");

    private final String family;
    private final String styleClass;

    MonoFont(final String family, final String styleClass) {
        this.family = family;
        this.styleClass = styleClass;
    }

    /**
     * The face's family name as the system lists it.
     *
     * @return the family name
     */
    public String family() {
        return family;
    }

    /**
     * The style class the shell root carries when this face is chosen.
     *
     * @return the style class
     */
    public String styleClass() {
        return styleClass;
    }

    /**
     * The first face of this list the system has.
     *
     * @param installed the families the system lists ({@code Font.getFamilies()})
     * @return the face to set the log in, or empty when the system has none of them
     */
    public static Optional<MonoFont> pick(final Collection<String> installed) {
        Objects.requireNonNull(installed, "installed");
        return Arrays.stream(values())
                .filter(face -> installed.contains(face.family))
                .findFirst();
    }
}
