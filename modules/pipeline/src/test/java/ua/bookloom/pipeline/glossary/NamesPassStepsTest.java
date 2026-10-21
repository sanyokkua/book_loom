package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.api.project.NamePolicy;

/**
 * The steps the names pass announces to its progress dialog: eight stage rows in the buttons' order, and one request
 * step per model call under the estimate planned after the scan.
 */
class NamesPassStepsTest extends NamesPassTestBase {

    private static final List<String> STAGES = List.of(
            "scan", "names", "terms-scan", "terms-review", "terms-read", "names-translate", "terms-translate", "write");

    private List<StepChanged> changes(final StepKind kind, final StepState state) {
        return events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filter(change -> change.kind() == kind && change.state() == state)
                .toList();
    }

    private List<String> labels(final StepKind kind, final StepState state) {
        return changes(kind, state).stream().map(StepChanged::label).toList();
    }

    // IF a stage were left out of the announcements, THEN the dialog would list fewer than the eight steps the pass
    // takes.
    @Test
    void run_fullPass_announcesEightStagesInOrder() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        final NamesPassReport report = succeeded(run(model()));

        assertThat(report).isNotNull();
        assertThat(events).contains(new StepsPlanned(StepKind.STAGE, 8, false));
        assertThat(labels(StepKind.STAGE, StepState.STARTED)).containsExactlyElementsOf(STAGES);
        assertThat(labels(StepKind.STAGE, StepState.ENDED)).containsExactlyElementsOf(STAGES);
        assertThat(changes(StepKind.STAGE, StepState.STARTED))
                .extracting(StepChanged::index)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    }

    // The scan of a held glossary, the scan of the words and the write send no call, so they end with no call.
    @Test
    void run_fullPass_stagesWithoutACallEndNoCallAndTheRestAreDone() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        assertThat(changes(StepKind.STAGE, StepState.ENDED))
                .filteredOn(change -> List.of("scan", "terms-scan", "write").contains(change.label()))
                .extracting(StepChanged::result)
                .containsOnly(StepResult.NO_CALL);
        assertThat(changes(StepKind.STAGE, StepState.ENDED))
                .filteredOn(change ->
                        List.of("names", "terms-read", "names-translate").contains(change.label()))
                .extracting(StepChanged::result)
                .containsOnly(StepResult.DONE);
    }

    // IF Stop is pressed, THEN the stages after the one running never start and none is shown as failed.
    @Test
    void run_stoppedBeforeStep_leavesLaterStagesUnstarted() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final AtomicInteger started = new AtomicInteger();

        fixture.pass().run(PROJECT, model(), null, event -> {
            events.add(event);
            if (event instanceof ModelCallStarted && started.incrementAndGet() == 4) {
                Thread.currentThread().interrupt();
            }
        });

        final List<String> begun = labels(StepKind.STAGE, StepState.STARTED);
        assertThat(begun).isNotEmpty().isSubsetOf(STAGES).doesNotContain("write");
        assertThat(begun).isEqualTo(STAGES.subList(0, begun.size()));
        assertThat(begun.size()).isLessThan(STAGES.size());
        assertThat(changes(StepKind.STAGE, StepState.ENDED))
                .extracting(StepChanged::result)
                .doesNotContain(StepResult.FAILED);
    }

    // The estimate the Names & style button shows is the number of request steps the dialog counts, as a forecast,
    // planned once the scan is over and before the first stage that asks.
    @Test
    void run_afterScan_plansEstimatedRequests() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        final StepsPlanned planned = new StepsPlanned(StepKind.REQUEST, 17, true);
        assertThat(events).containsOnlyOnce(planned);
        final int plannedAt = events.indexOf(planned);
        assertThat(events.get(plannedAt - 1))
                .isEqualTo(new StepChanged(StepKind.STAGE, "scan", 0, StepState.ENDED, null, StepResult.NO_CALL));
    }

    // One request step per model call, labelled by the stage that sent it, each ended done when the reply came.
    @Test
    void run_seededBook_announcesOneRequestStepPerModelCall() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final NamesPassModel model = model();

        succeeded(run(model));

        assertThat(changes(StepKind.REQUEST, StepState.STARTED)).hasSize(model.requests());
        assertThat(labels(StepKind.REQUEST, StepState.STARTED)).isSubsetOf(STAGES);
        assertThat(changes(StepKind.REQUEST, StepState.ENDED))
                .hasSize(model.requests())
                .extracting(StepChanged::result)
                .containsOnly(StepResult.DONE);
    }
}
