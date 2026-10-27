package ua.bookloom.ui.notify;

/** A dialog footer's one-line feedback slot, which a transient message is routed to while the dialog is open. */
public interface FooterFeedback {

    /**
     * Shows one line, replacing any earlier line.
     *
     * @param text the line
     * @param severity the status role that colours it
     */
    void feedback(String text, Severity severity);
}
