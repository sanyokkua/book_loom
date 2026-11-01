package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.FixedDialProfiles;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.RecordingProjectActions;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.i18n.Messages;

/** The estimate before Start follows the stored speeds and says whether it was measured and where. */
class BriefEstimateTest extends SettingsViewModelTestBase {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);

    private static final ModelSelection CHOSEN = new ModelSelection("ollama", "gemma");

    private BriefEstimate estimateOver(final int segments) {
        return estimateOver(segments, executor);
    }

    private BriefEstimate estimateOver(final int segments, final Executor background) {
        final ScriptedProjectService projects = new ScriptedProjectService();
        final CurrentProject current = new CurrentProject();
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.adopt(CHOSEN);
            OpenedBookFixture.open(current, "p1", "en", "uk");
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
        final FixedDialProfiles dial = new FixedDialProfiles();
        final BriefEstimate estimate = onFx(() -> new BriefEstimate(
                current, viewModel, project, modelSettings, speeds, dial, background, Platform::runLater));
        WaitForAsyncUtils.waitForFxEvents();
        return estimate;
    }

    private static StartEstimate shown(final BriefEstimate estimate) {
        return onFx(() -> estimate.estimate().get());
    }

    // IF the estimate worded every stored speed as the model test's, THEN a run's speed would be misdescribed.
    @Test
    void estimate_speedMeasuredByARun_namesTheRunAndKeepsItAfterARestart() {
        modelSettings.measured("ollama", "gemma", 3.0, MeasuredFrom.RUN);

        final BriefEstimate estimate = estimateOver(100);

        assertThat(shown(estimate))
                .isEqualTo(new StartEstimate.Ranged(Duration.ofMinutes(5), Duration.ofMinutes(10), MeasuredFrom.RUN));
    }

    // IF the estimate were not worked out again when a test stored a speed, THEN Start would keep showing the old line.
    @Test
    void estimate_speedStoredWhileShown_isWorkedOutAgainOnTheRevision() {
        final BriefEstimate estimate = estimateOver(100);
        assertThat(shown(estimate)).isEqualTo(new StartEstimate.None());

        modelSettings.measured("ollama", "gemma", 3.0, MeasuredFrom.TEST);
        onFx(() -> {
            speeds.bump();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(shown(estimate))
                .isEqualTo(new StartEstimate.Ranged(Duration.ofMinutes(5), Duration.ofMinutes(10), MeasuredFrom.TEST));
    }

    // IF the stored speed were read on the FX thread, THEN each change of the brief would wait there on the database.
    @Test
    void estimate_storedSpeed_isReadInTheBackgroundAndShownOnceRead() {
        modelSettings.measured("ollama", "gemma", 3.0, MeasuredFrom.RUN);
        final List<Runnable> held = new ArrayList<>();

        final BriefEstimate estimate = estimateOver(100, held::add);
        final StartEstimate beforeTheRead = shown(estimate);
        held.forEach(Runnable::run);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(beforeTheRead).isEqualTo(new StartEstimate.None());
        assertThat(shown(estimate))
                .isEqualTo(new StartEstimate.Ranged(Duration.ofMinutes(5), Duration.ofMinutes(10), MeasuredFrom.RUN));
    }

    static Stream<Arguments> wordings() {
        return Stream.of(
                Arguments.of(
                        "every planned kind measured by a run",
                        Map.of(
                                CallKind.DRAFT, new MeasuredSpeed(3.0, MeasuredFrom.RUN),
                                CallKind.REVIEW, new MeasuredSpeed(6.0, MeasuredFrom.RUN)),
                        300,
                        QualityDial.BALANCED,
                        List.of("For this book on this computer: about 17 min, measured on this model")),
                Arguments.of(
                        "only the draft speed of the model test, at Fast",
                        Map.of(CallKind.DRAFT, new MeasuredSpeed(3.0, MeasuredFrom.TEST)),
                        3000,
                        QualityDial.FAST,
                        List.of("For this book on this computer: about 2–3 h, estimate from the model test")),
                Arguments.of(
                        "a run's draft speed and a never-measured review, at Balanced",
                        Map.of(CallKind.DRAFT, new MeasuredSpeed(3.0, MeasuredFrom.RUN)),
                        300,
                        QualityDial.BALANCED,
                        List.of(
                                "For this book on this computer: about 15–30 min, estimate from an earlier translation")),
                Arguments.of("nothing measured", Map.of(), 300, QualityDial.BALANCED, List.of()));
    }

    // The plan of the spec's cases: 300 segments in 20 chunks, the reviewer once a chunk at Balanced and never at Fast.
    @ParameterizedTest(name = "{0}")
    @MethodSource("wordings")
    void line_measuredAndEstimate_wording(
            final String description,
            final Map<CallKind, MeasuredSpeed> stored,
            final int segments,
            final QualityDial dial,
            final List<String> expected) {
        final int passes = dial == QualityDial.FAST ? 0 : 1;

        assertThat(StartEstimates.of(stored, segments, 20, dial, passes).lines(EN))
                .containsExactlyElementsOf(expected);
    }
}
