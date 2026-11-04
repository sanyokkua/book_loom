package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestPlanners;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.context.PromptLint.Facts;
import ua.bookloom.pipeline.index.IndexTestBooks;

/** The no-garbage rule over a really planned batch prompt, its whole window and map included. */
class BatchPromptHygieneTest {

    private static final BookBrief BRIEF = BookBrief.defaults("en").withLanguages("en", "uk");
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "“Stay, Hale,” said Moreau."),
            new BatchItem("2", "1914"),
            new BatchItem("3", "⟦g0⟧Hello⟦g1⟧ there, Moreau."));
    private static final GlossaryEntry HALE =
            GlossaryEntry.of("h", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);

    @Test
    void plan_fullContext_passesTheLint() {
        final TranslatePlanner planner =
                TestPlanners.planner(BRIEF, IndexTestBooks.oneUnit("He left."), ModelProfile.defaults());
        final TranslateContext context = new TranslateContext(
                new Window(
                        List.of(
                                new WindowPair("x:0", "He left.", "Він пішов."),
                                new WindowPair("x:1", "It was late.", null)),
                        new Focus(List.of(), Map.of()),
                        "She smiled."),
                BookMap.of(List.of(HALE), List.of(), BRIEF),
                List.of("He left. → Він пішов.", "He left. → Він пішов."),
                Map.of(),
                List.of());
        final BatchPromptBuilder builder = new BatchPromptBuilder(planner);
        final PromptPlan plan = Objects.requireNonNull(builder.plan(context, ITEMS, List.of("x:2", "x:3", "x:4"))
                        .data())
                .plan();

        final List<ChatMessage> messages =
                builder.requestFor(plan, ITEMS, false).messages();

        final String chunk =
                String.join("\n", ITEMS.stream().map(BatchItem::masked).toList());
        assertThat(PromptLint.violations(
                        String.join(
                                "\n",
                                messages.stream().map(ChatMessage::content).toList()),
                        new Facts(chunk, List.of(), List.of())))
                .isEmpty();
        assertThat(plan.userText()).containsOnlyOnce("He left. → Він пішов.\n");
    }
}
