package ua.bookloom.api.pipeline;

/** Looks up what each quality-dial setting turns on, so the window shows the pipeline's own table. */
public interface DialProfiles {

    /**
     * The row of one dial setting.
     *
     * @param dial the setting; never null
     * @return its profile
     */
    DialProfile of(QualityDial dial);
}
