package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;

/** A prompt split into the parts it sent, in the order it sent them, so a person can read what a call carried. */
class PromptSectionsTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private static Map<String, String> batchValues() {
        final Map<String, String> values = new HashMap<>();
        values.put("source", "English");
        values.put("target", "Ukrainian");
        values.put("items", "<s id=\"1\">He left.</s>\n<s id=\"2\">She stayed.</s>");
        values.put("glossaryTerms", "Bartimaeus → Бартімеус");
        values.put("lockedNames", "");
        values.put("characters", "Bartimaeus — male");
        values.put("precedingPairs", "Source: Hi.\nTranslation: Привіт.");
        values.put("nextSource", "They met.");
        values.put("summary", "");
        return values;
    }

    // The order is the template's, not the map's: the batch template carries characters after key terms and before the
    // previous pairs, the single draft carries them before the memory.
    @Test
    void userSectionsOf_batchDraft_listsTheFilledSlotsInTemplateOrderAndLeavesEmptyOnesOut() {
        final List<PromptSection> sections = TEMPLATES.userSectionsOf(PromptName.DRAFT_BATCH_JSON, batchValues());

        assertThat(sections)
                .extracting(PromptSection::slot)
                .containsExactly("glossaryTerms", "characters", "precedingPairs", "nextSource", "items");
    }

    @Test
    void userSectionsOf_blockWithAHeading_holdsTheHeadingApartFromTheLinesAsSent() {
        final List<PromptSection> sections = TEMPLATES.userSectionsOf(PromptName.DRAFT_BATCH_JSON, batchValues());

        assertThat(sections)
                .filteredOn(section -> section.slot().equals("precedingPairs"))
                .singleElement()
                .extracting(PromptSection::heading, PromptSection::lines, PromptSection::origin)
                .containsExactly(
                        "[Previous pairs — context only; do NOT translate or answer them]",
                        List.of("Source: Hi.", "Translation: Привіт."),
                        PromptSection.Origin.USER);
    }

    @Test
    void userSectionsOf_standaloneSlot_takesTheLineAboveAsItsHeading() {
        final List<PromptSection> sections = TEMPLATES.userSectionsOf(PromptName.DRAFT_BATCH_JSON, batchValues());

        assertThat(sections.getLast())
                .extracting(PromptSection::slot, PromptSection::heading, PromptSection::lines)
                .containsExactly(
                        "items", "<Items>", List.of("<s id=\"1\">He left.</s>", "<s id=\"2\">She stayed.</s>"));
    }

    @Test
    void userSectionsOf_singleDraft_listsCharactersBeforeMemoryAndTheTextLast() {
        final Map<String, String> values = new HashMap<>();
        values.put("source", "English");
        values.put("target", "Ukrainian");
        values.put("tokens", "⟦g0⟧ ⟦g1⟧");
        values.put("text", "⟦g0⟧He⟦g1⟧ left.");
        values.put("summary", "A boy summons a djinni.");
        values.put("characters", "Nathaniel — male");
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

    // The style sheet is sent in the system message; a person asking "what did the model see" must see it too.
    @Test
    void sectionsOf_batchDraft_startsWithTheSystemStyleSheetThenTheUserParts() {
        final List<PromptSection> sections = TEMPLATES.sectionsOf(PromptName.DRAFT_BATCH_JSON, FRAME, batchValues());

        assertThat(sections.getFirst())
                .extracting(PromptSection::slot, PromptSection::heading, PromptSection::origin)
                .containsExactly("styleSheet", "Style:", PromptSection.Origin.SYSTEM);
        assertThat(sections.getFirst().lines()).isNotEmpty();
        assertThat(sections)
                .filteredOn(section -> section.origin() == PromptSection.Origin.USER)
                .extracting(PromptSection::slot)
                .containsExactly("glossaryTerms", "characters", "precedingPairs", "nextSource", "items");
    }

    @Test
    void userSectionsOf_undeclaredSlot_isRejectedAsRenderingIs() {
        final Map<String, String> values = new HashMap<>(batchValues());
        values.put("nope", "x");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TEMPLATES.userSectionsOf(PromptName.DRAFT_BATCH_JSON, values))
                .withMessageContaining("nope");
    }
}
