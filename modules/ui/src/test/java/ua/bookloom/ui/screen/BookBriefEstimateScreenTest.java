package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.TooltipProbe;

/**
 * The time estimate under the quality choice (book-brief "State a time estimate on the brief as measured or as an
 * estimate"): a range marked as an estimate when only the model test's draft speed is stored, no line when nothing is,
 * and never a time worked out from tokens per second alone.
 */
class BookBriefEstimateScreenTest extends BriefRunScreenTestBase {

    private static final String PROVIDER = "ollama";

    private void showWithSegments(final int total) throws Exception {
        projects.willList(List.of(
                new ProjectSummary(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME).project(), 0, total, null, null)));
        readyToStart();
        showBrief();
    }

    // IF a speed were not scaled to the book, THEN a 1,240-segment book would promise the time of a short one.
    @Test
    void estimate_speedOfThreeSecondsOnHundredSegmentsBalanced_isARangeFromTheModelTest() throws Exception {
        modelSettings.measured(PROVIDER, MODEL, 3.0);

        showWithSegments(100);

        assertThat(textOf("brief-estimate-line-1"))
                .isEqualTo("For this book on this computer: about 5–10 min, estimate from the model test");
    }

    // IF the line ignored the dial, THEN Max would show the time of Balanced.
    @Test
    void estimate_dialChangedToMax_followsTheDial() throws Exception {
        modelSettings.measured(PROVIDER, MODEL, 3.0);
        showWithSegments(100);

        onFx(() -> briefViewModel().setDial(QualityDial.MAX));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textOf("brief-estimate-line-1"))
                .isEqualTo("For this book on this computer: about 10–20 min, estimate from the model test");
    }

    // IF a time showed with nothing measured, THEN a guess of hours would be believed.
    @Test
    void estimate_noStoredSpeed_showsNoLineAndNoRelativeCost() throws Exception {
        showWithSegments(100);

        assertThat(isShown("brief-estimate-line-1")).isFalse();
        assertThat(textOf("brief-estimate")).doesNotContain("about").doesNotContain("Fast");
    }

    // The model's stored settings hold tokens per second elsewhere; a time is never worked out from them alone.
    @Test
    void estimate_modelSettingsWithoutAMeasuredSpeed_showsNoLine() throws Exception {
        modelSettings.save(ModelSettings.defaults(PROVIDER, MODEL));

        showWithSegments(100);

        assertThat(isShown("brief-estimate-line-1")).isFalse();
    }

    @Test
    void estimate_bookWithNoSegmentsKnown_showsNoLine() throws Exception {
        modelSettings.measured(PROVIDER, MODEL, 3.0);

        showWithSegments(0);

        assertThat(isShown("brief-estimate-line-1")).isFalse();
    }

    @Test
    void estimate_measuredLine_saysWhereItComesFromOnHover() throws Exception {
        modelSettings.measured(PROVIDER, MODEL, 3.0);
        showWithSegments(100);

        assertThat(TooltipProbe.tipText(required("brief-estimate-line-1")))
                .isEqualTo("Worked out from the seconds per segment the model test measured, scaled to this book and"
                        + " quality. A range, because not every kind of request was measured on this model yet.");
    }

    @Test
    void estimate_sitsInTheQualityCard() throws Exception {
        showWithSegments(100);

        assertThat(isInside("brief-estimate", "brief-quality-card")).isTrue();
    }
}
