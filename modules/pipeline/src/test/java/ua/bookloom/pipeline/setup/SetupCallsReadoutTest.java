package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
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
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/** A setup call says what it asked before the model answers, and the fields it got back after. */
class SetupCallsReadoutTest {

    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Hello.")));

    private final ScriptedChatModel model = new ScriptedChatModel();
    private final List<JobEvent> events = new ArrayList<>();
    private final JobModelCalls calls = new JobModelCalls(
            onSent -> {
                onSent.run();
                return model;
            },
            events::add,
            Clock.systemUTC(),
            "uk",
            ModelWindow.DEFAULT_WINDOW);

    private List<CallSnapshot> snapshots() {
        return events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .toList();
    }

    @Test
    void send_briefSynthesis_waitingSnapshotHoldsTheAskedLinesAndTheAnsweredOneEachReplyField() {
        model.answer(Result.ok(new ChatResponse("{\"genre\":\"fantasy\",\"audience\":\"adult\"}", FinishReason.STOP)));
        final Map<String, String> user = new LinkedHashMap<>();
        user.put("title", "The Lantern Ledger");
        user.put("notes", "");

        new SetupCalls(new PromptTemplates(), new ObjectMapper())
                .send(REQUEST, PromptName.BRIEF_SYNTHESIS, calls, SetupCalls.askedOf(user));

        assertThat(snapshots().getFirst().items())
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple("title: The Lantern Ledger", null));
        assertThat(snapshots().getLast().items())
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple("genre", "fantasy"), tuple("audience", "adult"));
    }

    @Test
    void ask_bookKnowledge_waitingSnapshotNamesTheTitleAndTheAnsweredOneTheKnownFields() {
        model.answer(Result.ok(new ChatResponse(
                "{\"known\":true,\"title\":\"The Lantern Ledger\",\"author\":\"Orsolya Venn\",\"year\":2013,"
                        + "\"genre\":\"fantasy novel\",\"confidence\":0.85}",
                FinishReason.STOP)));
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

        new BookKnowledge(new PromptTemplates(), new ObjectMapper())
                .ask(
                        new BookFacts("The Lantern Ledger", "Orsolya Venn", 2014, null, null),
                        "o_venn-the_lantern_ledger-2014",
                        frame,
                        calls);

        assertThat(snapshots().getFirst().items())
                .extracting(CallItem::id)
                .containsExactly("title: The Lantern Ledger", "author: Orsolya Venn");
        assertThat(snapshots().getLast().items())
                .extracting(CallItem::id, CallItem::target)
                .contains(tuple("known", "true"), tuple("genre", "fantasy novel"));
    }
}
