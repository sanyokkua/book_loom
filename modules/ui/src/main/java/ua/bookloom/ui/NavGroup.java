package ua.bookloom.ui;

import ua.bookloom.ui.i18n.MessageKey;

/** The two headed groups the navigation column is divided into, in the order it shows them. */
public enum NavGroup {
    /** The numbered steps from importing a book to exporting it. */
    WORKFLOW(MessageKey.NAV_GROUP_WORKFLOW),
    /** Screens about the application rather than one book. */
    APPLICATION(MessageKey.NAV_GROUP_APPLICATION);

    private final MessageKey heading;

    NavGroup(final MessageKey heading) {
        this.heading = heading;
    }

    /**
     * Returns the catalogue entry of this group's heading.
     *
     * @return the heading key; never null
     */
    public MessageKey heading() {
        return heading;
    }
}
