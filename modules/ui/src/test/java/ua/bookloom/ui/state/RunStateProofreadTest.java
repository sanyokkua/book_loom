package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/**
 * The run-state rows while the Proofread stage is in work or has windows left: the title of a stop, the live line
 * while it reads and once a finished run has windows a later fact left, and the welcome banner of a decided book.
 */
class RunStateProofreadTest {

    private static final Messages ENGLISH = new Messages((LocaleProvider) () -> Locale.ENGLISH);

    private static String toolbar(final RunStateView view) {
        return view.toolbar().stream()
                .map(action -> action.id().replace("translating-", "") + ":"
                        + action.label().in(ENGLISH) + ":" + (action.enabled() ? "on" : "off"))
                .collect(Collectors.joining("; "));
    }

    // IF a stop during Proofread read "Stopped at 100 %", THEN the windows still to read would look done.
    @Test
    void title_stoppedWithProofreadLeft_namesTheProofreadWindow() {
        final RunStateView view = RunInputs.in(RunState.STOPPED)
                .decided(142)
                .proofread(new ProofreadTally(1, 18, 5, 6))
                .view();

        assertThat(view.title().in(ENGLISH)).isEqualTo("Stopped · Proofread window 6 of 18");
    }

    // IF the live line said "Now translating." while Proofread reads, THEN the person would think drafting went on.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"ch2 · p05 | Now proofreading: ch2 · p05", "'' | Now proofreading."})
    void liveLine_runningProofread_saysProofreading(final String span, final String expected) {
        final RunStateView view = RunInputs.in(RunState.RUNNING)
                .stage(new RunStage(JobStage.PROOFREAD, ProofreadCoverage.MARKED_AND_DOUBTFUL, 1))
                .span(span)
                .view();

        assertThat(view.liveLine().in(ENGLISH)).isEqualTo(expected);
    }

    // IF the welcome banner of a fully decided book promised "the first segment not done", THEN it would name work
    // that does not exist; it names the Proofread window Continue reads first.
    @Test
    void banner_reopenedWithProofreadLeft_namesTheProofreadWindow() {
        final RunStateView view = RunInputs.in(RunState.IDLE)
                .decided(142)
                .proofread(new ProofreadTally(1, 18, 5, 6))
                .view();

        assertThat(Objects.requireNonNull(Objects.requireNonNull(view.banner()).text())
                        .in(ENGLISH))
                .isEqualTo("Continue translation goes on with Proofread at window 6 of 18.");
    }

    // IF a finished run with Proofread windows a later fact left still read "All segments translated.", THEN its
    // Continue would not say what it does.
    @Test
    void liveLine_completedWithProofreadLeft_continuesWithProofread() {
        final RunStateView view = RunInputs.in(RunState.COMPLETED)
                .decided(142)
                .controls(Controls.of(RunState.COMPLETED, false, true))
                .proofread(new ProofreadTally(1, 18, 5, 6))
                .view();

        assertThat(view.liveLine().in(ENGLISH)).isEqualTo("Continues with Proofread at window 6 of 18.");
        assertThat(toolbar(view)).contains("start:Continue translation:on");
    }
}
