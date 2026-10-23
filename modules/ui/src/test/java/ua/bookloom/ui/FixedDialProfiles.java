package ua.bookloom.ui;

import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.QualityDial;

/** The three quality rows of the Settings specification, written out by hand (:ui may not see the pipeline's own table). */
public final class FixedDialProfiles implements DialProfiles {

    @Override
    public DialProfile of(final QualityDial dial) {
        return switch (dial) {
            case FAST -> new Row(1, 1, 0, false, false, 8);
            case BALANCED -> new Row(2, 2, 1, false, false, 8);
            case MAX -> new Row(3, 3, 2, true, true, 2);
        };
    }

    private record Row(
            int precedingTargets,
            int repairRounds,
            int reviewPasses,
            boolean backwardRevision,
            boolean llmSummary,
            int chunkCap)
            implements DialProfile {}
}
