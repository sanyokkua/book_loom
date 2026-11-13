package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.NamesPassStage;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;

/**
 * The check step of the names pass reads the staged suggestions against the target language's rules and sends no
 * request; accepting stays the person's act (Accept all), so every suggestion is still a suggestion afterwards.
 */
class NamesPassCheckTest extends NamesPassTestBase {

    private List<JobEvent> between(final String first, final String second) {
        final int from = indexOf(first, StepState.ENDED);
        final int to = indexOf(second, StepState.STARTED);
        return events.subList(from, to);
    }

    private int indexOf(final String label, final StepState state) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof StepChanged change
                    && change.kind() == StepKind.STAGE
                    && change.state() == state
                    && change.label().equals(label)) {
                return i;
            }
        }
        throw new AssertionError("no stage " + label + " " + state);
    }

    // IF the check asked the model, THEN a step the dialog shows as "no request" would hide a call.
    @Test
    void run_checkStage_sendsNoRequestAndEndsWithNoCall() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        final String check = NamesPassStage.CHECK.code();
        assertThat(events)
                .filteredOn(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filteredOn(change -> change.kind() == StepKind.STAGE
                        && change.state() == StepState.ENDED
                        && change.label().equals(check))
                .extracting(StepChanged::result)
                .containsExactly(StepResult.NO_CALL);
        assertThat(between("terms-translate", check)).noneMatch(ModelCallStarted.class::isInstance);
        assertThat(between(check, NamesPassStage.WRITE.code())).noneMatch(ModelCallStarted.class::isInstance);
    }

    // IF the check accepted what it validates, THEN Accept all would be done without the person pressing it.
    @Test
    void run_afterCheck_suggestedTargetsAreStillSuggestions() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        final List<GlossaryEntry> suggested = fixture.glossaryNow().stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .filter(GlossaryEntry::isSuggested)
                .toList();
        assertThat(suggested).extracting(GlossaryEntry::term).contains("Brann", "Wren", "Harrowgate");
        assertThat(held("Brann").isSuggested()).isTrue();
    }
}
