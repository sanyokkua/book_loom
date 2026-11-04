package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.context.FrameFixtures.BUILDER;
import static ua.bookloom.pipeline.context.FrameFixtures.DIALOGUE;
import static ua.bookloom.pipeline.context.FrameFixtures.MARKED;
import static ua.bookloom.pipeline.context.FrameFixtures.NARRATION;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;

class ContextBuilderTest {

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

    @Test
    void plan_analysisKind_roleDataAndReplyOnly() {
        final PromptPlan plan = plan(CallKind.TERM_READING, FrameFixtures.focus(NARRATION), FrameFixtures.frame(BOOK));

        assertThat(plan.blocks())
                .filteredOn(block -> block.role() == Block.Role.SYSTEM)
                .extracting(Block::slot)
                .containsExactly(BlockSlots.ROLE, BlockSlots.DATA, BlockSlots.REPLY);
    }

    @Test
    void plan_reviewKind_validationError() {
        final RunFrame frame = FrameFixtures.frame(BOOK);

        final Result<PromptPlan> plan = BUILDER.plan(
                CallKind.REVIEW,
                FrameFixtures.focus(NARRATION),
                FrameFixtures.emptyMap(frame.brief()),
                frame,
                ModelProfile.defaults());

        assertThat(plan.isErr()).isTrue();
        assertThat(Objects.requireNonNull(plan.error()).code()).isEqualTo(ErrorCode.validation);
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
}
