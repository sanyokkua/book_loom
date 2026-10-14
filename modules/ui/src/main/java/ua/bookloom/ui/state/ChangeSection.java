package ua.bookloom.ui.state;

import ua.bookloom.ui.i18n.MessageKey;

/** The table of Names &amp; style a row of an operation's results belongs to. */
public enum ChangeSection {
    /** A row of the glossary. */
    NAMES(MessageKey.RESULTS_SECTION_NAMES),
    /** A row of the recurring terms. */
    TERMS(MessageKey.RESULTS_SECTION_TERMS);

    private final MessageKey heading;

    ChangeSection(final MessageKey heading) {
        this.heading = heading;
    }

    /**
     * The words of the section's heading in a sectioned list.
     *
     * @return the catalogue key, whose argument is the number of rows in the section
     */
    public MessageKey heading() {
        return heading;
    }
}
