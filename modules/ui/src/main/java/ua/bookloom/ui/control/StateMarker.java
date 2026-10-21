package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A state shown as an icon and a word on one of six colours, so a person learns the colours once and they mean the
 * same on a list row, a chip and a banner, and never carry the state alone.
 */
public final class StateMarker extends Label {

    private static final String BASE_CLASS = "state-marker";

    /** The six things a state can mean; each has one style class, one Feather icon and one word. */
    public enum Meaning {
        /** Queued or idle; grey. */
        WAITING("state-waiting", Feather.CLOCK, MessageKey.STATE_WAITING),
        /** At work; blue. */
        WORKING("state-working", Feather.LOADER, MessageKey.STATE_WORKING),
        /** Finished well; green. */
        DONE("state-done", Feather.CHECK_CIRCLE, MessageKey.STATE_DONE),
        /** Needs the person, or is slow; amber. */
        ATTENTION("state-attention", Feather.ALERT_TRIANGLE, MessageKey.STATE_ATTENTION),
        /** Failed or unreachable; red. */
        FAILED("state-failed", Feather.X_CIRCLE, MessageKey.STATE_FAILED),
        /** Left as it was on purpose; sand. */
        KEPT("state-kept", Feather.MINUS_CIRCLE, MessageKey.STATE_KEPT);

        private final String styleClass;
        private final Feather icon;
        private final MessageKey word;

        Meaning(final String styleClass, final Feather icon, final MessageKey word) {
            this.styleClass = styleClass;
            this.icon = icon;
            this.word = word;
        }

        /** {@return the style class that gives the marker its colours in the theme} */
        public String styleClass() {
            return styleClass;
        }

        /** {@return the Feather icon drawn before the word} */
        public Feather icon() {
            return icon;
        }

        /** {@return the catalogue entry of the word the marker shows by default} */
        public MessageKey word() {
            return word;
        }
    }

    private StateMarker(final String text, final Meaning meaning) {
        super(text, iconOf(meaning));
        getStyleClass().addAll("chip", BASE_CLASS, meaning.styleClass());
        setMinWidth(Region.USE_PREF_SIZE);
    }

    private static FontIcon iconOf(final Meaning meaning) {
        final FontIcon icon = new FontIcon(meaning.icon());
        icon.getStyleClass().add("state-icon");
        return icon;
    }

    /**
     * Builds the marker with the meaning's own word.
     *
     * @param messages the catalogue of the display language
     * @param meaning what the marker says
     * @return a new marker
     */
    public static StateMarker of(final Messages messages, final Meaning meaning) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(meaning, "meaning");
        return new StateMarker(messages.get(meaning.word()), meaning);
    }

    /**
     * Builds the marker with a more specific word, for a place where the state has a name of its own ("translated",
     * "no answer for 3:40").
     *
     * @param meaning what the marker says
     * @param word the already translated word shown beside the icon
     * @return a new marker
     */
    public static StateMarker of(final Meaning meaning, final String word) {
        Objects.requireNonNull(meaning, "meaning");
        return new StateMarker(Objects.requireNonNull(word, "word"), meaning);
    }
}
