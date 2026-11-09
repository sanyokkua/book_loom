package ua.bookloom.pipeline.revision;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.proofread.SweepInputs;
import ua.bookloom.pipeline.proofread.TermSweep;

/**
 * Lets the export's consistency pass run the Proofread stage's {@link TermSweep}: the pass's inputs become the sweep's,
 * and each change the sweep reports is counted in the pass's tally. It goes with the pass.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SweepAdapter {

    static SweepInputs inputsOf(final PassInputs inputs) {
        return new SweepInputs(inputs.projectId(), inputs.frame().targetLanguage(), inputs.gate(), inputs.sources());
    }

    static TermSweep.Observer countingIn(final PassInputs inputs, final PassTally tally) {
        return new TermSweep.Observer() {
            @Override
            public void swept(final String segmentId, final int terms) {
                tally.swept(segmentId, inputs.locator(segmentId), terms);
            }

            @Override
            public void proposed(final String segmentId) {
                tally.proposed(segmentId, inputs.locator(segmentId));
            }
        };
    }
}
