package ua.bookloom.pipeline.dial;

import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.QualityDial;

/** The window's view of the dial table: the very rows a run reads, never a copy. */
public final class DialParameterProfiles implements DialProfiles {

    /** Bound by the pipeline module. */
    public DialParameterProfiles() {
        // stateless
    }

    @Override
    public DialProfile of(final QualityDial dial) {
        return DialParameters.of(dial);
    }
}
