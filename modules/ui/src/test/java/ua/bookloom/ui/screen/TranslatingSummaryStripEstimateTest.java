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

/** The strip's big line before Start repeats the brief's estimate line, word for word, or nothing. */
class TranslatingSummaryStripEstimateTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);

    private static String bigTextFor(final StartEstimate estimate) {
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
                null,
                null);
        return TranslatingSummaryStrip.bigText(RunStateView.of(inputs), Throughput.EMPTY, estimate, EN);
    }

    // IF the strip worded a run's speed as the model test's, THEN the same estimate would be described twice
    // differently.
    @Test
    void bigText_readyWithARangeFromARun_namesTheEarlierTranslation() {
        assertThat(bigTextFor(
                        new StartEstimate.Ranged(Duration.ofMinutes(5), Duration.ofMinutes(10), MeasuredFrom.RUN)))
                .isEqualTo("For this book on this computer: about 5–10 min, estimate from an earlier translation");
    }

    @Test
    void bigText_readyMeasuredOnThisModel_saysMeasured() {
        assertThat(bigTextFor(new StartEstimate.Measured(Duration.ofMinutes(8))))
                .isEqualTo("For this book on this computer: about 8 min, measured on this model");
    }

    @Test
    void bigText_readyWithNothingMeasured_isBlank() {
        assertThat(bigTextFor(new StartEstimate.None())).isEmpty();
    }
}
