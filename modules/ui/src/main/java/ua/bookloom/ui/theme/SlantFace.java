package ua.bookloom.ui.theme;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The face a suggested glossary target is slanted in. JavaFX draws macOS's system font upright even when the
 * stylesheet asks for italic (its italic glyphs are the regular ones, pixel for pixel), so a target nobody confirmed
 * would not read as slanted there. A stylesheet cannot list fallbacks for a face, so the shell root carries the chosen
 * face's style class and the stylesheet names the face under it; with none installed the field keeps the system face
 * and the likely chip beside it.
 */
public enum SlantFace {
    /** Windows: the system face itself, which has a real italic. */
    SEGOE_UI("Segoe UI", "slant-segoe"),
    /** macOS: the sans face closest to the system's that has a real italic. */
    HELVETICA_NEUE("Helvetica Neue", "slant-helvetica"),
    /** Most Linux desktops. */
    DEJAVU_SANS("DejaVu Sans", "slant-dejavu"),
    /** Red Hat and Fedora desktops. */
    LIBERATION_SANS("Liberation Sans", "slant-liberation"),
    /** Any system with the common core fonts. */
    ARIAL("Arial", "slant-arial");

    private final String family;
    private final String styleClass;

    SlantFace(final String family, final String styleClass) {
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
     * @return the face to slant a suggestion in, or empty when the system has none of them
     */
    public static Optional<SlantFace> pick(final Collection<String> installed) {
        Objects.requireNonNull(installed, "installed");
        return Arrays.stream(values())
                .filter(face -> installed.contains(face.family))
                .findFirst();
    }
}
