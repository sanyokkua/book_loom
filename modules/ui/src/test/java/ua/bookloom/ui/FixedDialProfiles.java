package ua.bookloom.ui;

import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;

/** The three quality rows of the Settings specification, written out by hand (:ui may not see the pipeline's own table). */
public final class FixedDialProfiles implements DialProfiles {

    @Override
    public DialProfile of(final QualityDial dial) {
        return switch (dial) {
            case FAST -> new Row(RepairMode.MARKS_ONLY, 0, false, false);
            case BALANCED -> new Row(RepairMode.ONE, 1, false, false);
            case MAX -> new Row(RepairMode.ONE, 2, true, true);
        };
    }

    private record Row(RepairMode repair, int reviewPasses, boolean backwardRevision, boolean llmSummary)
            implements DialProfile {}
}
