package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchContext.Pair;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.context.PromptLint.Facts;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The no-garbage rule over a really built batch prompt, whole context and item-prefixed locked names included. */
class BatchPromptHygieneTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "“Stay, ⟦g0⟧,” said Hale."),
            new BatchItem("2", "1914"),
            new BatchItem("3", "⟦g0⟧Hello⟦g1⟧ there, Moreau."));

    @Test
    void messagesFor_fullContextWithItemPrefixedLockedNames_passesTheLint() {
        final BatchContext context = new BatchContext(
                new DraftContext(
                        List.of(),
                        "Hale searches for his brother.",
                        List.of("Hale → Гейл (character, male)", "1: ⟦g0⟧ → Бартімеус, person, male"),
                        List.of("He left. → Він пішов.")),
                List.of(new Pair("He left.", "Він пішов."), new Pair("It was late.", "Було пізно.")),
                "She smiled.",
                List.of("Hale — male"));

        final List<ChatMessage> messages =
                new BatchPromptBuilder(new PromptTemplates(), FRAME).messagesFor(context, ITEMS);

        final String chunk =
                String.join("\n", ITEMS.stream().map(BatchItem::masked).toList());
        assertThat(PromptLint.violations(
                        String.join(
                                "\n",
                                messages.stream().map(ChatMessage::content).toList()),
                        new Facts(chunk, List.of(), List.of())))
                .isEmpty();
    }
}
