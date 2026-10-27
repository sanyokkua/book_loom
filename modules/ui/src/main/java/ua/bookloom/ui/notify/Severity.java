package ua.bookloom.ui.notify;

import org.kordamp.ikonli.feather.Feather;
import ua.bookloom.ui.state.Tone;

/**
 * The four severities of a transient message.
 *
 * <p>Each carries the tone that binds it to its status role in the theme and its icon, so the mapping from
 * severity to colour and glyph lives in one place instead of in a switch beside every use. A warning and an error also
 * stay longer than a success or an information message.
 */
public enum Severity {
    /** Something the person asked for went well; drawn in the success role. */
    SUCCESS(Tone.OK, Feather.CHECK_CIRCLE, false),
    /** Neutral information; drawn in the information role. */
    INFO(Tone.INFO, Feather.INFO, false),
    /** The outcome is fine but needs a look; drawn in the warning role. */
    WARNING(Tone.WARN, Feather.ALERT_TRIANGLE, true),
    /** Something went wrong; drawn in the danger role. */
    ERROR(Tone.ERR, Feather.ALERT_OCTAGON, true);

    private static final String FAMILY = "toast";

    private final Tone tone;
    private final Feather icon;
    private final boolean persistent;

    Severity(final Tone tone, final Feather icon, final boolean persistent) {
        this.tone = tone;
        this.icon = icon;
        this.persistent = persistent;
    }

    /** Whether a message of this severity stays on the longer lifetime: it asks to be read, not glanced at. */
    boolean isPersistent() {
        return persistent;
    }

    public String styleClass() {
        return tone.styleClass(FAMILY);
    }

    Feather icon() {
        return icon;
    }
}
