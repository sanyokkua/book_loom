package ua.bookloom.ui.state;

/**
 * The status colour role an activity-log entry is drawn in.
 *
 * <p>Each carries the style class that binds it to its token in the theme, so the mapping from role to colour lives
 * in one place. The class is never the only carrier of meaning: {@link LogKind#mark()} repeats it as a glyph.
 */
public enum StatusRole {
    /** Something went as intended; drawn in the success token. */
    SUCCESS("status-ok"),
    /** Neutral information; drawn in the information token. */
    INFO("status-info"),
    /** Worth a look but not a failure; drawn in the warning token. */
    WARNING("status-warn"),
    /** Something went wrong; drawn in the danger token. */
    DANGER("status-err");

    private final String styleClass;

    StatusRole(final String styleClass) {
        this.styleClass = styleClass;
    }

    /**
     * The theme style class of this role.
     *
     * @return a non-blank CSS class name
     */
    public String styleClass() {
        return styleClass;
    }
}
