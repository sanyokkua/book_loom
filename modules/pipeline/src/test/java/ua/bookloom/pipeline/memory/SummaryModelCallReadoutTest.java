package ua.bookloom.pipeline.memory;

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

/** The summary call says which chapter it summarizes before the model answers, and the summary it wrote after. */
class SummaryModelCallReadoutTest {

    @Test
    void summarize_answered_snapshotsAskTheChapterThenShowTheSummary() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(
                        new ChatResponse("{\"summary\":{\"target\":\"Клерк знайшов книгу.\"}}", FinishReason.STOP)));
        final List<JobEvent> events = new ArrayList<>();
        final JobModelCalls calls = new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                events::add,
                Clock.systemUTC(),
                "uk",
                ModelWindow.DEFAULT_WINDOW);
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

        new SummaryModelCall(new PromptTemplates(), new ObjectMapper(), frame, calls)
                .summarize("", "A clerk finds\na ledger.", "Клерк знаходить книгу.");

        final List<CallSnapshot> shown = events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .toList();
        assertThat(shown.getFirst().items())
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple("A clerk finds a ledger.", null));
        assertThat(shown.getLast().items())
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple("A clerk finds a ledger.", "Клерк знайшов книгу."));
    }
}
