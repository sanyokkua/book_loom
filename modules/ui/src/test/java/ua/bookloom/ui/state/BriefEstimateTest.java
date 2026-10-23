package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.RecordingProjectActions;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;

/** The estimate before Start follows the stored speed and says where it was measured. */
class BriefEstimateTest extends SettingsViewModelTestBase {

    private static final ModelSelection CHOSEN = new ModelSelection("ollama", "gemma");

    private BriefEstimate estimateOver(final int segments) {
        final ScriptedProjectService projects = new ScriptedProjectService();
        final CurrentProject current = new CurrentProject();
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.adopt(CHOSEN);
            OpenBookForTest.open(current, "p1", "en", "uk");
            return null;
        });
        final Project stored = ProjectFixtures.project(
                "p1",
                "Frankenstein.epub",
                ProjectFixtures.BOOK_HASH,
                "en",
                "uk",
                NameOrigin.AUTO,
                "Frankenstein",
                Instant.parse("2026-10-02T10:00:00Z"));
        projects.willList(List.of(new ProjectSummary(stored, 0, segments, null, null)));
        final BriefProject project = onFx(() ->
                new BriefProject(current, projects, new RecordingProjectActions(), new RecordingToasts(), executor));
        WaitForAsyncUtils.waitForFxEvents();
        return onFx(() -> new BriefEstimate(current, viewModel, project, modelSettings, speeds));
    }

    private static StartEstimate shown(final BriefEstimate estimate) {
        return onFx(() -> estimate.estimate().get());
    }

    // IF the estimate worded every stored speed as the model test's, THEN a run's speed would be misdescribed.
    @Test
    void estimate_speedMeasuredByARun_namesTheRunAndKeepsItAfterARestart() {
        modelSettings.measured("ollama", "gemma", 3.0, MeasuredFrom.RUN);

        final BriefEstimate estimate = estimateOver(100);

        assertThat(shown(estimate)).isEqualTo(new StartEstimate.Measured(Duration.ofSeconds(450), MeasuredFrom.RUN));
    }

    // IF the estimate were not worked out again when a test stored a speed, THEN Start would keep showing the old line.
    @Test
    void estimate_speedStoredWhileShown_isWorkedOutAgainOnTheRevision() {
        final BriefEstimate estimate = estimateOver(100);
        assertThat(shown(estimate)).isEqualTo(new StartEstimate.Relative());

        modelSettings.measured("ollama", "gemma", 3.0, MeasuredFrom.TEST);
        onFx(() -> {
            speeds.bump();
            return null;
        });

        assertThat(shown(estimate)).isEqualTo(new StartEstimate.Measured(Duration.ofSeconds(450), MeasuredFrom.TEST));
    }
}
