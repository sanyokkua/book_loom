package ua.bookloom.ui.notify;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Which dialog footer, if any, is open to take a message that would otherwise be a toast.
 *
 * <p>The dialog host and the work layer each register the card they show, so the transient-message stack needs to know
 * neither of them. A card of the dialog host lies above the work layer's, so it is asked first. FX Application Thread
 * only.
 */
@Slf4j
@Singleton
public final class DialogFeedback {

    /** The two layers that show a dialog; the earlier constant lies above the later. */
    public enum Layer {
        /** The shell's dialog host: questions, forms, results. */
        MODAL,
        /** The work layer: the progress dialog of visible model work. */
        WORK
    }

    private final Map<Layer, FooterFeedback> open = new EnumMap<>(Layer.class);

    /** Creates the registry with no dialog open. */
    @Inject
    public DialogFeedback() {
        // nothing to prepare: no dialog is open yet
    }

    /**
     * Records the dialog a layer now shows.
     *
     * @param layer the layer showing it
     * @param footer the dialog's footer line, replacing the layer's earlier dialog
     */
    public void open(final Layer layer, final FooterFeedback footer) {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(footer, "footer");
        log.debug("feedback: the {} layer shows a dialog", layer);
        open.put(layer, footer);
    }

    /**
     * Records that a layer shows no dialog now.
     *
     * @param layer the layer that closed its dialog
     */
    public void close(final Layer layer) {
        Objects.requireNonNull(layer, "layer");
        log.debug("feedback: the {} layer shows no dialog", layer);
        open.remove(layer);
    }

    /**
     * Puts a line in the topmost open dialog's footer.
     *
     * @param text the line
     * @param severity the status role that colours it
     * @return {@code true} when a dialog took it, {@code false} when none is open
     */
    public boolean offer(final String text, final Severity severity) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(severity, "severity");
        for (final Layer layer : Layer.values()) {
            final FooterFeedback footer = open.get(layer);
            if (footer != null) {
                log.debug("feedback: the line goes to the footer of the {} layer's dialog", layer);
                footer.feedback(text, severity);
                return true;
            }
        }
        log.debug("feedback: no dialog is open");
        return false;
    }
}
