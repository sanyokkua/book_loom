package ua.bookloom.ui.notify;

import org.kordamp.ikonli.feather.Feather;

/**
 * The four severities of a transient message.
 *
 * <p>Each carries the style class that binds it to its status role in the theme and its icon, so the mapping from
 * severity to colour and glyph lives in one place instead of in a switch beside every use.
 */
enum Severity {
    /** Something the person asked for went well; drawn in the success role. */
    SUCCESS("toast-ok", Feather.CHECK_CIRCLE),
    /** Neutral information; drawn in the information role. */
    INFO("toast-info", Feather.INFO),
    /** The outcome is fine but needs a look; drawn in the warning role. */
    WARNING("toast-warn", Feather.ALERT_TRIANGLE),
    /** Something went wrong; drawn in the danger role. */
    ERROR("toast-err", Feather.ALERT_OCTAGON);

    private final String styleClass;
    private final Feather icon;

    Severity(final String styleClass, final Feather icon) {
        this.styleClass = styleClass;
        this.icon = icon;
    }

    String styleClass() {
        return styleClass;
    }

    Feather icon() {
        return icon;
    }
}
