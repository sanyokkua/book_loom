package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/** The garbled-word call says which texts it asked about, then the doubted words it found in each. */
class ModelWordValidatorReadoutTest {

    private static final List<String> TEXTS =
            List.of("Він швидко побіг до лісу.", "Він сидів на кафедрахрі й мовчки дивився.");

    @Test
    void findAll_answered_snapshotsAskTheTextsThenShowWhatWasDoubtedInEach() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(
                        "{\"words\":[{\"id\":\"2\",\"word\":\"кафедрахрі\","
                                + "\"quote\":\"сидів на кафедрахрі й мовчки\"}]}",
                        FinishReason.STOP)));
        final List<JobEvent> events = new ArrayList<>();
        final JobModelCalls calls = callsOver(model, events);
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

        new ModelWordValidator(new PromptTemplates(), new ObjectMapper(), calls, frame).findAll(TEXTS);

        final List<CallSnapshot> shown = events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .toList();
        assertThat(shown.getFirst().items()).extracting(CallItem::id).containsExactlyElementsOf(TEXTS);
        assertThat(shown.getLast().items())
                .extracting(
                        CallItem::id,
                        item -> item.target() != null && item.target().contains("кафедрахрі"))
                .containsExactly(tuple(TEXTS.get(0), false), tuple(TEXTS.get(1), true));
        assertThat(shown.getLast().items().getFirst().target()).isEqualTo("no suspicious words");
    }

    private static JobModelCalls callsOver(final ScriptedChatModel model, final List<JobEvent> events) {
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                events::add,
                Clock.systemUTC(),
                "uk",
                ModelWindow.DEFAULT_WINDOW);
    }
}
