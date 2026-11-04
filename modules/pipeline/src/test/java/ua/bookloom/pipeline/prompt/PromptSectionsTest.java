package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestPlanners;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.context.WindowPair;
import ua.bookloom.pipeline.index.IndexTestBooks;

/** A prompt split into the parts it sent, in the order it sent them, so a person can read what a call carried. */
class PromptSectionsTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();

    private static final GlossaryEntry ZERUBIN =
            GlossaryEntry.of("z", "p1", "Zerubin", "Зерубін", TermType.CHARACTER, Gender.MALE, false);

    // A batch planned by the run's context builder: a previous pair, the next source and a name the items say.
    private static PromptPlan batchPlan() {
        final BookBrief brief = BookBrief.defaults("en").withLanguages("en", "uk");
        final TranslatePlanner planner =
                TestPlanners.planner(brief, IndexTestBooks.oneUnit("He left."), ModelProfile.defaults());
        final TranslateContext context = new TranslateContext(
                new Window(
                        List.of(new WindowPair("x:0", "Hi.", "Привіт.")), new Focus(List.of(), Map.of()), "They met."),
                BookMap.of(List.of(ZERUBIN), List.of(), brief),
                List.of(),
                Map.of(),
                List.of());
        final List<BatchItem> items = List.of(new BatchItem("1", "Zerubin left."), new BatchItem("2", "She stayed."));
        return Objects.requireNonNull(new BatchPromptBuilder(planner)
                        .plan(context, items, List.of("x:1", "x:2"))
                        .data())
                .plan();
    }

    @Test
    void sections_batchPlan_listsTheBlocksInTheOrderSentSystemFirst() {
        final List<PromptSection> sections = batchPlan().sections();

        assertThat(sections)
                .extracting(PromptSection::slot)
                .containsSubsequence("role", "data", "styleSheet", "reply", "map", "window", "task", "items");
        assertThat(sections.getFirst().origin()).isEqualTo(PromptSection.Origin.SYSTEM);
        assertThat(sections.getLast().origin()).isEqualTo(PromptSection.Origin.USER);
    }

    @Test
    void sections_blockWithAHeading_holdsTheHeadingApartFromTheLinesAsSent() {
        assertThat(batchPlan().sections())
                .filteredOn(section -> section.slot().equals("window"))
                .singleElement()
                .satisfies(section -> {
                    assertThat(section.heading())
                            .isEqualTo("[Previous pairs — context only; do NOT translate or answer them]");
                    assertThat(section.lines()).startsWith("Source: Hi.", "Translation: Привіт.");
                    assertThat(section.origin()).isEqualTo(PromptSection.Origin.USER);
                });
    }

    @Test
    void sections_itemsBlock_takesItsOpeningLineAsItsLines() {
        assertThat(batchPlan().sections().getLast())
                .extracting(PromptSection::slot, PromptSection::heading)
                .containsExactly("items", "");
        assertThat(batchPlan().sections().getLast().lines())
                .startsWith("<Items>", "<s id=\"1\">Zerubin left.</s>", "<s id=\"2\">She stayed.</s>");
    }

    @Test
    void userSectionsOf_singleDraft_listsCharactersBeforeMemoryAndTheTextLast() {
        final Map<String, String> values = new HashMap<>();
        values.put("source", "English");
        values.put("target", "Ukrainian");
        values.put("tokens", "⟦g0⟧ ⟦g1⟧");
        values.put("text", "⟦g0⟧He⟦g1⟧ left.");
        values.put("summary", "A boy summons a djinni.");
        values.put("characters", "Edwin — male");
        values.put("memoryHint", "He left. → Він пішов.");
        values.put("precedingTargets", "");

        final List<PromptSection> sections = TEMPLATES.userSectionsOf(PromptName.DRAFT, values);

        assertThat(sections)
                .extracting(PromptSection::slot, PromptSection::heading)
                .containsExactly(
                        tuple("summary", "[Book so far — context only; do NOT re-translate it]"),
                        tuple("characters", "[Characters in this text — keep their gender and agreement]"),
                        tuple("memoryHint", "[Earlier decisions — keep consistent]"),
                        tuple("tokens", "[Immutable tokens for this text]"),
                        tuple("text", "<Text>"));
    }

    // The token line is a sentence with the slot inside it; the section holds the line as the model read it.
    @Test
    void userSectionsOf_slotInsideAHeadedLine_holdsTheWholeRenderedLine() {
        final Map<String, String> values =
                Map.of("source", "English", "target", "Ukrainian", "tokens", "⟦g0⟧", "text", "⟦g0⟧Hi");

        assertThat(TEMPLATES.userSectionsOf(PromptName.DRAFT, values))
                .filteredOn(section -> section.slot().equals("tokens"))
                .singleElement()
                .extracting(PromptSection::lines)
                .isEqualTo(List.of(
                        "Copy this exact ordered sequence unchanged: ⟦g0⟧",
                        "Do not add, reorder, split, translate, or omit these tokens."));
    }

    @Test
    void userSectionsOf_undeclaredSlot_isRejectedAsRenderingIs() {
        final Map<String, String> values =
                new HashMap<>(Map.of("source", "English", "target", "Ukrainian", "text", "Hi"));
        values.put("nope", "x");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TEMPLATES.userSectionsOf(PromptName.DRAFT, values))
                .withMessageContaining("nope");
    }
}
