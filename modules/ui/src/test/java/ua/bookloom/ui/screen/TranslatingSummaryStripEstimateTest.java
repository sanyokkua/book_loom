package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.ModelChoice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateInputs;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.StartEstimate;
import ua.bookloom.ui.state.Throughput;

/** The strip's big line before Start repeats the estimate and says where the speed was measured. */
class TranslatingSummaryStripEstimateTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);

    private static String bigTextFor(final MeasuredFrom source) {
        final RunStateInputs inputs = new RunStateInputs(
                RunState.IDLE,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                142,
                0,
                0,
                new ModelChoice("Ollama", "gemma4:e4b", true),
                Controls.of(RunState.IDLE, false, false),
                null,
                null,
                null);
        return TranslatingSummaryStrip.bigText(
                RunStateView.of(inputs),
                Throughput.EMPTY,
                new StartEstimate.Measured(Duration.ofMinutes(8), source),
                EN);
    }

    // IF the strip worded a run's speed as the model test's, THEN the same estimate would be described twice
    // differently.
    @Test
    void bigText_readyWithASpeedFromARun_namesTheEarlierTranslation() {
        assertThat(bigTextFor(MeasuredFrom.RUN))
                .isEqualTo("For this book on this computer: about 8 min (estimate from an earlier translation)");
    }

    @Test
    void bigText_readyWithASpeedFromTheTest_namesTheModelTest() {
        assertThat(bigTextFor(MeasuredFrom.TEST))
                .isEqualTo("For this book on this computer: about 8 min (estimate from the model test)");
    }
}
