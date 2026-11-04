package ua.bookloom.pipeline.context;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.OutputLimit;

/**
 * The one recipe every model call's context comes from (design D8): the run frame's fixed system blocks first, then
 * one last user message with the call's own blocks, the reminder last. A block with nothing to say is left out, so no
 * call carries filler. Not yet called by the run: task 11.5 moves the drafts, batches and the repair onto it.
 */
@Slf4j
public final class ContextBuilder {

    // The system blocks are instructions written in English; the user blocks are the book's text.
    private static final String INSTRUCTIONS_LANGUAGE = "en";

    private final BlockWords words = BlockWords.bundled();

    /** Builds with the bundled block wording. */
    @Inject
    public ContextBuilder() {
        // The wording is bundled; nothing is injected until the window, map and budgets (tasks 11.3, 11.4) are.
    }

    /** Which fixed part a kind of call sends. */
    private enum FrameKind {
        TRANSLATE,
        ANALYSIS,
        NONE
    }

    /**
     * Plans one call's prompt.
     *
     * @param kind the non-null kind of call
     * @param focus the non-null items the call works on; at least one for a translate-family call
     * @param map the non-null book map, whose window lines task 11.3 adds
     * @param frame the non-null run frame
     * @param profile the non-null profile of the model the call goes to
     * @return the plan, or {@link ErrorCode#validation} for a kind the builder does not plan or a translate-family
     *     call with no item, or {@link ErrorCode#internal} when the plan cannot be built
     */
    public Result<PromptPlan> plan(
            final CallKind kind,
            final Focus focus,
            final BookMap map,
            final RunFrame frame,
            final ModelProfile profile) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(profile, "profile");
        final FrameKind frameKind = frameOf(kind);
        log.debug(
                "Planning {} ({} frame) for {} items",
                kind,
                frameKind,
                focus.items().size());
        if (frameKind == FrameKind.NONE) {
            return invalid(kind, "This kind of call is not built by the context builder.");
        }
        if (frameKind == FrameKind.TRANSLATE && focus.items().isEmpty()) {
            return invalid(kind, "A translation call needs at least one item.");
        }
        try {
            return Result.ok(build(kind, frameKind, focus, frame, profile));
        } catch (RuntimeException cause) {
            log.error("Planning {} for {} items failed", kind, focus.items().size(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal, "Prompt failed", "The call's prompt could not be built.", null, cause));
        }
    }

    private PromptPlan build(
            final CallKind kind,
            final FrameKind frameKind,
            final Focus focus,
            final RunFrame frame,
            final ModelProfile profile) {
        final List<Block> blocks =
                new ArrayList<>(frameKind == FrameKind.TRANSLATE ? frame.system() : frame.analysis());
        if (frameKind == FrameKind.TRANSLATE) {
            translateBlocks(kind, focus, frame, blocks);
        } else if (!focus.items().isEmpty()) {
            blocks.add(user(BlockSlots.ITEMS, items(focus, List.of())));
        }
        final @Nullable String source = frame.brief().sourceLanguage();
        final int tokens = blocks.stream()
                .mapToInt(block -> TokenEstimator.estimate(
                        block.text(), block.role() == Block.Role.SYSTEM ? INSTRUCTIONS_LANGUAGE : source))
                .sum();
        final PromptPlan plan = new PromptPlan(
                blocks, tokens, outputCap(focus, frame, profile), ModelWindow.of(profile.context(), null));
        logPlan(kind, focus, plan);
        return plan;
    }

    private void translateBlocks(
            final CallKind kind, final Focus focus, final RunFrame frame, final List<Block> blocks) {
        final String target = Objects.requireNonNull(frame.brief().targetLanguage(), "targetLanguage");
        if (kind == CallKind.REPAIR || kind == CallKind.STRUCTURAL_REPAIR) {
            final List<String> defects = defects(focus);
            log.debug("Plan {}: {} defect lines", kind, defects.size());
            if (!defects.isEmpty()) {
                blocks.add(user(
                        BlockSlots.KIND_EXTRA,
                        words.get(BlockWords.REPAIR_HEADING) + "\n" + String.join("\n", defects)));
            }
        }
        blocks.add(user(
                BlockSlots.TASK,
                RunFrames.languages(
                        words.get(BlockWords.TASK_TRANSLATE), frame.brief().sourceLanguage(), target)));
        blocks.add(user(BlockSlots.ITEMS, items(focus, Reminder.of(focus.items(), frame))));
    }

    private List<String> defects(final Focus focus) {
        final List<String> lines = new ArrayList<>();
        for (int index = 0; index < focus.items().size(); index++) {
            final String id = Integer.toString(index + 1);
            focus.reasons().getOrDefault(focus.items().get(index).segmentId(), List.of()).stream()
                    .filter(reason -> !reason.isBlank())
                    .forEach(reason -> lines.add(words.get(BlockWords.REPAIR_LINE)
                            .replace("{id}", id)
                            .replace("{reason}", reason)));
        }
        return lines;
    }

    private String items(final Focus focus, final List<String> reminder) {
        final List<String> lines = new ArrayList<>();
        lines.add(words.get(BlockWords.ITEMS_OPEN));
        for (int index = 0; index < focus.items().size(); index++) {
            lines.add(words.get(BlockWords.ITEM)
                    .replace("{id}", Integer.toString(index + 1))
                    .replace("{text}", focus.items().get(index).maskedSource()));
        }
        lines.add(words.get(BlockWords.ITEMS_CLOSE));
        lines.addAll(reminder);
        return String.join("\n", lines);
    }

    // Until task 11.4 brings the output reserve, a batch is capped as a batch draft is today.
    private static int outputCap(final Focus focus, final RunFrame frame, final ModelProfile profile) {
        final String target = frame.brief().targetLanguage();
        if (focus.items().isEmpty() || target == null) {
            return profile.context().outputMax();
        }
        final List<String> sources =
                focus.items().stream().map(FocusItem::maskedSource).toList();
        return OutputLimit.forBatch(sources, frame.brief().sourceLanguage(), target)
                .capTokens();
    }

    private static FrameKind frameOf(final CallKind kind) {
        return switch (kind) {
            case DRAFT, REPAIR, STRUCTURAL_REPAIR -> FrameKind.TRANSLATE;
            case PRESCAN,
                    REVIEW_TERMS,
                    SUGGEST_TARGETS,
                    SUMMARY,
                    FILE_NAME,
                    BRIEF_SUGGESTION,
                    BOOK_KNOWLEDGE,
                    BRIEF_SYNTHESIS,
                    TERM_READING,
                    TERM_CHOICE,
                    NAME_KNOWLEDGE -> FrameKind.ANALYSIS;
            case REVIEW, DIRECTED_FIX, REVISION -> FrameKind.NONE;
        };
    }

    private static Result<PromptPlan> invalid(final CallKind kind, final String message) {
        log.debug("Plan {} refused: {}", kind, message);
        return Result.err(AppError.of(ErrorCode.validation, "Prompt not planned", message));
    }

    private static Block user(final String slot, final String text) {
        final Block.Stability stability =
                switch (slot) {
                    case BlockSlots.SCENE -> Block.Stability.CHAPTER;
                    case BlockSlots.MAP, BlockSlots.WINDOW -> Block.Stability.WINDOW;
                    default -> Block.Stability.CALL;
                };
        return new Block(slot, Block.Role.USER, stability, text);
    }

    private static void logPlan(final CallKind kind, final Focus focus, final PromptPlan plan) {
        log.debug(
                "Plan {} items={} blocks={} promptTokens={} maxOutputTokens={} contextSize={}",
                kind,
                focus.items().size(),
                plan.blocks().stream()
                        .map(block -> block.slot() + ":" + block.text().length())
                        .toList(),
                plan.promptTokens(),
                plan.maxOutputTokens(),
                plan.contextSize());
        if (log.isTraceEnabled()) {
            plan.blocks().forEach(block -> log.trace("Plan {} block {}: {}", kind, block.slot(), block.text()));
        }
    }
}
