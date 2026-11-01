package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** A names call says what it asked before the model answers, and what its reply was read as after. */
class CallReadoutSitesTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private final ObjectMapper mapper = new ObjectMapper();

    /** Answers with a fixed reply and keeps what the call site described and what it said it read. */
    private static final class Recording implements ModelCalls {

        private final String reply;
        private final List<List<String>> asked = new ArrayList<>();
        private final List<CallItem> read = new ArrayList<>();

        Recording(final String reply) {
            this.reply = reply;
        }

        @Override
        public Result<ChatResponse> call(
                final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
            return Result.ok(new ChatResponse(reply, FinishReason.STOP));
        }

        @Override
        public Result<ChatResponse> callAbout(
                final CallKind kind,
                final List<String> segmentIds,
                final ChatRequest request,
                final CallDescriptor descriptor) {
            asked.add(descriptor.asked());
            return call(kind, null, request);
        }

        @Override
        public void read(final List<CallItem> items) {
            read.addAll(items);
        }
    }

    private static GlossaryEntry character(final String term) {
        return GlossaryEntry.of("p:" + term, "p", term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    @Test
    void ask_nameKnowledge_describesTheNamesThenSaysWhatEachWasReadAs() {
        final Recording calls = new Recording(
                "{\"items\":[{\"term\":\"Ivar\",\"gender\":\"male\",\"category\":\"person\",\"confidence\":0.9}]}");
        final NameKnowledgeCalls knowledge = new NameKnowledgeCalls(new PromptTemplates(), mapper);

        knowledge.ask(List.of(character("Ivar"), character("Mira")), FRAME, calls);

        // A name the model leaves out is asked once more, alone.
        assertThat(calls.asked).containsExactly(List.of("Ivar", "Mira"), List.of("Mira"));
        assertThat(calls.read)
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple("Ivar", "male, character"), tuple("Mira", null), tuple("Mira", null));
    }

    @Test
    void choose_termChoice_describesTheTermsThenSaysKeepOrDrop() {
        final Recording calls = new Recording(
                "{\"terms\":[{\"term\":\"pentacle\",\"keep\":true}," + "{\"term\":\"table\",\"keep\":false}]}");
        final BookSlice book = GlossaryTestSegments.of(
                List.of("The master drew the pentacle on the floor.", "She put the table by the door."));

        new TermChoice(new PromptTemplates(), mapper).choose(List.of("pentacle", "table"), book, FRAME, calls);

        assertThat(calls.asked).containsExactly(List.of("pentacle", "table"));
        assertThat(calls.read)
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple("pentacle", "keep"), tuple("table", "drop"));
    }
}
