package ua.bookloom.ui.state;

import java.util.Arrays;
import java.util.List;

/**
 * The status colour a banner, a toast, a log entry or a status line is drawn in.
 *
 * <p>Every family of nodes names its style classes {@code <family>-ok}, {@code -info}, {@code -warn} and {@code -err},
 * so one tone serves them all and the theme keeps one rule per family and tone. The colour is never the only carrier
 * of meaning: each use also has its glyph or its words.
 */
public enum Tone {
    /** Something went as intended; drawn in the success token. */
    OK("ok"),
    /** Neutral information; drawn in the information token. */
    INFO("info"),
    /** Worth a look but not a failure; drawn in the warning token. */
    WARN("warn"),
    /** Something went wrong; drawn in the danger token. */
    ERR("err");

    private static final String STATUS_FAMILY = "status";
    private static final List<String> STATUS_CLASSES =
            Arrays.stream(values()).map(Tone::statusClass).toList();

    private final String suffix;

    Tone(final String suffix) {
        this.suffix = suffix;
    }

    /**
     * The style class of this tone within one family of nodes.
     *
     * @param family the family's prefix, such as {@code banner} or {@code toast}
     * @return {@code <family>-<tone>}, such as {@code banner-warn}
     */
    public String styleClass(final String family) {
        return family + "-" + suffix;
    }

    /** {@return the class of a plain status word or line in this tone, such as {@code status-ok}} */
    public String statusClass() {
        return styleClass(STATUS_FAMILY);
    }

    /** {@return the status classes of every tone, for a node that drops its old tone before taking a new one} */
    public static List<String> statusClasses() {
        return STATUS_CLASSES;
    }
}
