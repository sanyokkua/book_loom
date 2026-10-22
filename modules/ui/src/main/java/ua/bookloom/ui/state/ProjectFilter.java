package ua.bookloom.ui.state;

import ua.bookloom.ui.i18n.MessageKey;

/** The All, In progress and Finished choices over the Projects list. */
public enum ProjectFilter {
    /** Every project. */
    ALL(MessageKey.PROJECTS_FILTER_ALL, MessageKey.PROJECTS_FILTER_ALL_TIP),
    /** Projects not started, being translated or stopped by an error. */
    IN_PROGRESS(MessageKey.PROJECTS_FILTER_IN_PROGRESS, MessageKey.PROJECTS_FILTER_IN_PROGRESS_TIP),
    /** Projects whose translation is finished or exported. */
    FINISHED(MessageKey.PROJECTS_FILTER_FINISHED, MessageKey.PROJECTS_FILTER_FINISHED_TIP);

    private final MessageKey label;
    private final MessageKey tip;

    ProjectFilter(final MessageKey label, final MessageKey tip) {
        this.label = label;
        this.tip = tip;
    }

    /** {@return the catalogue entry of the segment's caption} */
    public MessageKey label() {
        return label;
    }

    /** {@return the catalogue entry of the segment's hover explanation} */
    public MessageKey tip() {
        return tip;
    }

    /**
     * Tells whether a project of this status stays in the list under this filter.
     *
     * @param status the project's status
     * @return {@code true} if the row is shown
     */
    public boolean admits(final ProjectStatus status) {
        return switch (this) {
            case ALL -> true;
            case IN_PROGRESS -> status != ProjectStatus.FINISHED && status != ProjectStatus.EXPORTED;
            case FINISHED -> status == ProjectStatus.FINISHED || status == ProjectStatus.EXPORTED;
        };
    }
}
