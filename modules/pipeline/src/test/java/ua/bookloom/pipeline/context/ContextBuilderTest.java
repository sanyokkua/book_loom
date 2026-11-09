package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.context.FrameFixtures.BUILDER;
import static ua.bookloom.pipeline.context.FrameFixtures.DIALOGUE;
import static ua.bookloom.pipeline.context.FrameFixtures.MARKED;
import static ua.bookloom.pipeline.context.FrameFixtures.NARRATION;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

class ContextBuilderTest {

    private static final GlossaryEntry ZOTOV =
            GlossaryEntry.of("z", "p1", "Зотов", "Зотов", TermType.CHARACTER, Gender.MALE, false);

    private static final Document BOOK = FrameFixtures.book(NARRATION, DIALOGUE, MARKED, NARRATION);

    private static PromptPlan plan(final CallKind kind, final Focus focus, final RunFrame frame) {
        final Result<PromptPlan> plan =
                BUILDER.plan(kind, focus, FrameFixtures.emptyMap(frame.brief()), frame, ModelProfile.defaults());
        assertThat(plan.isOk()).as("plan of %s", kind).isTrue();
        return Objects.requireNonNull(plan.data());
    }

    @Test
    void plan_draft_systemBlocksInOrder() {
        final PromptPlan plan = plan(CallKind.DRAFT, FrameFixtures.focus(DIALOGUE, MARKED), FrameFixtures.frame(BOOK));

        assertThat(plan.blocks())
                .filteredOn(block -> block.role() == Block.Role.SYSTEM)
                .extracting(Block::slot)
                .containsExactly(
                        BlockSlots.ROLE,
                        BlockSlots.DATA,
                        BlockSlots.BOOK_RULES,
                        BlockSlots.STYLE_SHEET,
                        BlockSlots.LANGUAGE_RULES,
                        BlockSlots.EXAMPLES,
                        BlockSlots.REPLY);
        assertThat(plan.blocks())
                .filteredOn(block -> block.role() == Block.Role.SYSTEM)
                .extracting(Block::stability)
                .containsOnly(Block.Stability.RUN);
        assertThat(plan.blocks().subList(0, 7)).allMatch(block -> block.role() == Block.Role.SYSTEM);
        assertThat(plan.blocks().getLast().slot()).isEqualTo(BlockSlots.ITEMS);
        assertThat(plan.blocks().getLast().text())
                .contains("<s id=\"2\">" + MARKED + "</s>")
                .endsWith("Keep every ⟦…⟧ mark exactly once.\nDialogue lines start with «— ».");
        assertThat(plan.promptTokens()).isPositive();
        assertThat(plan.maxOutputTokens()).isPositive();
        assertThat(plan.contextSize()).isEqualTo(ModelProfile.DEFAULT_HARD_CAP);
    }

    @Test
    void plan_twoCallsOfOneRun_systemPartByteIdentical() {
        final RunFrame frame = FrameFixtures.frame(BOOK);
        final Focus batch = FrameFixtures.focus(NARRATION, DIALOGUE, MARKED, NARRATION);
        final Focus repair = new Focus(
                List.of(new FocusItem("ch1:2", MARKED)), Map.of("ch1:2", List.of("function-word residue: что")));

        final PromptPlan draft = plan(CallKind.DRAFT, batch, frame);
        final PromptPlan fix = plan(CallKind.REPAIR, repair, frame);

        assertThat(fix.systemText()).isEqualTo(draft.systemText());
        assertThat(fix.userText()).isNotEqualTo(draft.userText()).contains("function-word residue: что");
        assertThat(fix.blocks()).extracting(Block::slot).contains(BlockSlots.KIND_EXTRA);
        assertThat(draft.blocks()).extracting(Block::slot).doesNotContain(BlockSlots.KIND_EXTRA);
    }

    @Test
    void plan_sameInputs_samePrompt() {
        final RunFrame first = FrameFixtures.frame(BOOK);
        final RunFrame second = FrameFixtures.frame(BOOK);

        assertThat(second.frameHash()).isEqualTo(first.frameHash()).matches("[0-9a-f]{64}");
        assertThat(plan(CallKind.DRAFT, FrameFixtures.focus(MARKED), second))
                .isEqualTo(plan(CallKind.DRAFT, FrameFixtures.focus(MARKED), first));
    }

    private static Window windowOfThree() {
        return new Window(
                List.of(
                        new WindowPair("ch1:0", NARRATION, "Зранку туман лежав над річкою."),
                        new WindowPair("ch1:1", DIALOGUE, null)),
                new Focus(List.of(new FocusItem("ch1:2", "Зотов ждал у моста.")), Map.of()),
                NARRATION);
    }

    @Test
    void plan_windowAndMap_mapThenWindowBlocks_declineSentenceOnce() {
        final RunFrame frame = FrameFixtures.frame(BOOK);
        final BookMap map = BookMap.of(List.of(ZOTOV), List.of(), frame.brief());

        final PromptPlan plan = Objects.requireNonNull(
                BUILDER.plan(CallKind.DRAFT, windowOfThree(), map, frame, ModelProfile.defaults(), List.of())
                        .data());
        final String all = plan.systemText() + "\n" + plan.userText();

        assertThat(plan.blocks())
                .extracting(Block::slot)
                .containsSubsequence(BlockSlots.REPLY, BlockSlots.MAP, BlockSlots.WINDOW, BlockSlots.TASK);
        assertThat(plan.blocks())
                .filteredOn(block -> block.slot().equals(BlockSlots.MAP))
                .singleElement()
                .satisfies(block -> {
                    assertThat(block.stability()).isEqualTo(Block.Stability.WINDOW);
                    assertThat(block.text()).contains("Зотов → Зотов (character, male)");
                });
        assertThat(plan.blocks())
                .filteredOn(block -> block.slot().equals(BlockSlots.WINDOW))
                .singleElement()
                .satisfies(block -> assertThat(block.text())
                        .contains("Source: " + NARRATION + "\nTranslation: Зранку туман лежав над річкою.")
                        .contains("Source: " + DIALOGUE)
                        .contains("[Next item after the batch"));
        assertThat(all.split(Pattern.quote("decline them to fit, keep the stem"), -1))
                .hasSize(2);
        assertThat(all).doesNotContain("exactly as shown");
    }

    @Test
    void plan_emptyMapAndWindow_noMapOrWindowBlock() {
        final PromptPlan plan = plan(CallKind.DRAFT, FrameFixtures.focus(NARRATION), FrameFixtures.frame(BOOK));

        assertThat(plan.blocks()).extracting(Block::slot).doesNotContain(BlockSlots.MAP, BlockSlots.WINDOW);
    }

    @Test
    void plan_analysisKind_roleDataAndReplyOnly() {
        final PromptPlan plan = plan(CallKind.TERM_READING, FrameFixtures.focus(NARRATION), FrameFixtures.frame(BOOK));

        assertThat(plan.blocks())
                .filteredOn(block -> block.role() == Block.Role.SYSTEM)
                .extracting(Block::slot)
                .containsExactly(BlockSlots.ROLE, BlockSlots.DATA, BlockSlots.REPLY);
    }

    @Test
    void plan_draftWithoutItems_validationError() {
        final RunFrame frame = FrameFixtures.frame(BOOK);

        final Result<PromptPlan> plan = BUILDER.plan(
                CallKind.DRAFT,
                FrameFixtures.focus(),
                FrameFixtures.emptyMap(frame.brief()),
                frame,
                ModelProfile.defaults());

        assertThat(Objects.requireNonNull(plan.error()).code()).isEqualTo(ErrorCode.validation);
    }

    // The reply is capped at the output reserve: 1.5 × 19 source tokens + 16 for the one item, for ru→uk with no ratio.
    @Test
    void plan_draftOfOneItem_capsTheReplyAtTheReserve() {
        final PromptPlan plan = plan(CallKind.DRAFT, FrameFixtures.focus(NARRATION), FrameFixtures.frame(BOOK));

        assertThat(plan.maxOutputTokens()).isEqualTo(45);
    }

    // A model that always reasons gets at least 2048 tokens for its reply.
    @Test
    void plan_alwaysReasoningModel_reservesTheReasoningFloor() {
        final RunFrame frame = FrameFixtures.frame(BOOK);
        final ModelProfile thinking = FrameFixtures.withReasoning(ReasoningControl.Kind.ALWAYS);

        final PromptPlan plan = Objects.requireNonNull(BUILDER.plan(
                        CallKind.DRAFT,
                        FrameFixtures.focus(NARRATION),
                        FrameFixtures.emptyMap(frame.brief()),
                        frame,
                        thinking)
                .data());

        assertThat(plan.maxOutputTokens()).isEqualTo(2048);
    }

    // The template's overhead is counted once per call.
    @Test
    void plan_profileWithTemplateOverhead_countsItOnce() {
        final RunFrame frame = FrameFixtures.frame(BOOK);
        final Focus focus = FrameFixtures.focus(NARRATION, DIALOGUE);
        final PromptPlan bare = plan(CallKind.DRAFT, focus, frame);

        final PromptPlan withOverhead = Objects.requireNonNull(BUILDER.plan(
                        CallKind.DRAFT,
                        focus,
                        FrameFixtures.emptyMap(frame.brief()),
                        frame,
                        FrameFixtures.withOverhead(135))
                .data());

        assertThat(withOverhead.promptTokens() - bare.promptTokens()).isEqualTo(135);
    }

    @Test
    void plan_midChapterDraft_sceneBlockHoldsTheCard() {
        final Document book = FrameFixtures.book("Зотов ждал у моста.", NARRATION, NARRATION, NARRATION, NARRATION);
        final RunFrame frame = FrameFixtures.frame(book);
        final Window window = new Window(
                List.of(new WindowPair("ch1:2", NARRATION, null)),
                new Focus(List.of(new FocusItem("ch1:3", NARRATION)), Map.of()),
                NARRATION);

        final PromptPlan plan = Objects.requireNonNull(BUILDER.plan(
                        CallKind.DRAFT,
                        window,
                        BookMap.of(List.of(ZOTOV), List.of(), frame.brief()),
                        frame,
                        ModelProfile.defaults(),
                        List.of())
                .data());

        assertThat(plan.blocks())
                .extracting(Block::slot)
                .containsSubsequence(BlockSlots.REPLY, BlockSlots.SCENE, BlockSlots.WINDOW, BlockSlots.TASK);
        assertThat(plan.blocks())
                .filteredOn(block -> block.slot().equals(BlockSlots.SCENE))
                .singleElement()
                .satisfies(block -> {
                    assertThat(block.stability()).isEqualTo(Block.Stability.CHAPTER);
                    assertThat(block.text())
                            .endsWith("Scene so far: Chapter 1, after paragraph 2. Recently present, not in this"
                                    + " passage: Зотов → Зотов (male).");
                });
    }
}
