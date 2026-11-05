package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.TestPlanners;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptBreakdown;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.ChunkBudget;

/** The system prefix the budget reserves for a batch call. */
class ContextWindowBudgetTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    @Test
    void staticPrefix_batchSystemMessage_fitsTheReservedPrefix() {
        final TranslatePlanner planner = TestPlanners.planner(
                BookBrief.defaults("en").withLanguages("en", "uk"),
                IndexTestBooks.oneUnit("He left."),
                ModelProfile.defaults());
        final List<BatchItem> items = List.of(new BatchItem("1", "He left."));
        final BatchPromptBuilder builder = new BatchPromptBuilder(planner);
        final PromptPlan plan = Objects.requireNonNull(builder.plan(TestPlanners.empty(planner), items, List.of("x:0"))
                        .data())
                .plan();
        final int actual = PromptBreakdown.of(
                        builder.requestFor(plan, items, false).messages())
                .systemTokens();

        assertThat(ChunkBudget.staticPrefix(FRAME)).isGreaterThanOrEqualTo(actual);
    }
}
