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
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.OutputLimit;

/**
 * The one recipe every model call's context comes from (design D8): the run frame's fixed system blocks first, then
 * one last user message with the call's own blocks, the reminder last. A block with nothing to say is left out, so no
 * call carries filler. Every translate-family plan is fitted to the model tier's budgets and capped at its output
 * reserve. Not yet called by the run: task 11.5 moves the drafts, batches and the repair onto it, and with them the
 * person's own window override, which the plan does not read yet.
 */
@Slf4j
public final class ContextBuilder {

    // The block wording is English; the style sheet, the language rules, the examples and the map are in the target,
    // and the window and the items are the book's text.
    private static final String INSTRUCTIONS_LANGUAGE = "en";
    private static final int REPLY_SHARE_DIVISOR = 2;

    private final BlockWords words = BlockWords.bundled();

    /** Builds with the bundled block wording. */
    @Inject
    public ContextBuilder() {
        // The wording is bundled and the budgets are pure; nothing is injected.
    }

    /** Which fixed part a kind of call sends. */
    private enum FrameKind {
        TRANSLATE,
        ANALYSIS,
        NONE
    }

    /**
     * Plans one call's prompt with no window: the focus alone, no previous pair, no next source.
     *
     * @param kind the non-null kind of call
     * @param focus the non-null items the call works on; at least one for a translate-family call
     * @param map the non-null book map
     * @param frame the non-null run frame
     * @param profile the non-null profile of the model the call goes to
     * @return as {@link #plan(CallKind, Window, BookMap, RunFrame, ModelProfile, List)}
     */
    public Result<PromptPlan> plan(
            final CallKind kind,
            final Focus focus,
            final BookMap map,
            final RunFrame frame,
            final ModelProfile profile) {
        Objects.requireNonNull(focus, "focus");
        return plan(kind, Window.of(focus), map, frame, profile, List.of());
    }

    /**
     * Plans one call's prompt.
     *
     * @param kind the non-null kind of call
     * @param window the non-null window: the items the call works on, at least one for a translate-family call, with
     *     the segments before them and the next source
     * @param map the non-null book map, whose entries the window names reach block 9
     * @param frame the non-null run frame
     * @param profile the non-null profile of the model the call goes to
     * @param masks the non-null masks of the focus items, whose tokens a locked entry is listed by; empty when nothing
     *     is hidden
     * @return the plan, or {@link ErrorCode#validation} for a kind the builder does not plan or a translate-family
     *     call with no item, or {@link ErrorCode#internal} when the plan cannot be built
     */
    public Result<PromptPlan> plan(
            final CallKind kind,
            final Window window,
            final BookMap map,
            final RunFrame frame,
            final ModelProfile profile,
            final List<ProtectedMask> masks) {
        return planFitted(kind, window, map, frame, profile, masks).map(FittedPlan::plan);
    }

    /** What a call's user blocks read beside the run frame. */
    private record CallWindow(Window window, BookMap map, List<ProtectedMask> masks) {}

    /**
     * A plan with how many of the focus items it kept, from the first, so a caller drafts the rest in a later call.
     *
     * @param plan the plan
     * @param keptItems how many of the focus items the plan carries; all of them for an analysis call
     */
    public record FittedPlan(PromptPlan plan, int keptItems) {}

    /**
     * Plans one call's prompt fitted to its budgets, as {@link #plan(CallKind, Window, BookMap, RunFrame, ModelProfile,
     * List)} does, and says how many items it kept.
     *
     * @param kind the non-null kind of call
     * @param window the non-null window
     * @param map the non-null book map
     * @param frame the non-null run frame
     * @param profile the non-null profile of the model the call goes to
     * @param masks the non-null masks of the focus items, in the items' order
     * @return the fitted plan, or the errors {@link #plan(CallKind, Window, BookMap, RunFrame, ModelProfile, List)}
     *     names
     */
    public Result<FittedPlan> planFitted(
            final CallKind kind,
            final Window window,
            final BookMap map,
            final RunFrame frame,
            final ModelProfile profile,
            final List<ProtectedMask> masks) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(masks, "masks");
        final Focus focus = window.focus();
        final FrameKind frameKind = frameOf(kind);
        log.debug(
                "Planning {} ({} frame) for {} items, {} previous",
                kind,
                frameKind,
                focus.items().size(),
                window.previous().size());
        final @Nullable String refusal = refusalOf(frameKind, focus);
        if (refusal != null) {
            return invalid(kind, refusal);
        }
        try {
            return Result.ok(
                    frameKind == FrameKind.TRANSLATE
                            ? translate(kind, new CallWindow(window, map, masks), frame, profile)
                            : analysis(kind, focus, frame, profile));
        } catch (RuntimeException cause) {
            log.error("Planning {} for {} items failed", kind, focus.items().size(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal, "Prompt failed", "The call's prompt could not be built.", null, cause));
        }
    }

    // The window, its map lines and the reserve are fitted to the tier's budgets, then rendered once more as sent.
    private FittedPlan translate(
            final CallKind kind, final CallWindow call, final RunFrame frame, final ModelProfile profile) {
        final int contextSize = ModelWindow.of(profile.context(), null);
        final WindowBudgets.Budget budget = new WindowBudgets.Budget(
                WindowBudgets.of(profile.tier()),
                contextSize,
                frame.brief().sourceLanguage(),
                frame.brief().targetLanguage());
        // The earlier chapters' summaries reach block 8 in group 12; until then a call has none.
        final WindowBudgets.Fitted fitted =
                WindowBudgets.fit(budget, call.window(), List.of(), new PlanSizer(kind, call, frame, profile));
        final PromptPlan plan = new PromptPlan(
                translateBlocks(kind, fitted.window(), fitted.lines(), frame),
                fitted.promptTokens(),
                fitted.reserve(),
                contextSize);
        logPlan(kind, fitted.window().focus(), plan);
        return new FittedPlan(plan, fitted.keptItems());
    }

    private FittedPlan analysis(
            final CallKind kind, final Focus focus, final RunFrame frame, final ModelProfile profile) {
        final List<Block> blocks = new ArrayList<>(frame.analysis());
        if (!focus.items().isEmpty()) {
            blocks.add(user(BlockSlots.ITEMS, items(focus, List.of())));
        }
        final int contextSize = ModelWindow.of(profile.context(), null);
        final int cap = kind == CallKind.SUMMARY
                ? OutputLimit.forSummary().capTokens()
                : profile.context().outputMax();
        final PromptPlan plan = new PromptPlan(
                blocks,
                promptTokens(blocks, frame, profile),
                Math.min(cap, contextSize / REPLY_SHARE_DIVISOR),
                contextSize);
        logPlan(kind, focus, plan);
        return new FittedPlan(plan, focus.items().size());
    }

    /** Measures a candidate window as the builder would send it. */
    private final class PlanSizer implements WindowBudgets.Sizer {

        private final CallKind kind;
        private final CallWindow call;
        private final RunFrame frame;
        private final ModelProfile profile;

        PlanSizer(final CallKind kind, final CallWindow call, final RunFrame frame, final ModelProfile profile) {
            this.kind = kind;
            this.call = call;
            this.frame = frame;
            this.profile = profile;
        }

        @Override
        public List<MapEntry> map(final Window window) {
            final @Nullable String target = frame.brief().targetLanguage();
            return WindowMap.selectCounted(
                    window,
                    List.of(),
                    call.map(),
                    masksOf(window, call.masks()),
                    LexiconFilter.of(frame.brief().sourceLanguage(), target),
                    target);
        }

        @Override
        public int promptTokens(final Window window, final MapLines lines, final List<String> summaries) {
            return ContextBuilder.promptTokens(translateBlocks(kind, window, lines, frame), frame, profile);
        }

        @Override
        public int reserve(final Focus focus) {
            final int sourceTokens = focus.items().stream()
                    .mapToInt(item -> TokenEstimator.estimate(
                            item.maskedSource(), frame.brief().sourceLanguage()))
                    .sum();
            return OutputReserve.of(
                    sourceTokens,
                    focus.items().size(),
                    frame.outputRatio(),
                    profile.reasoning().kind(),
                    ModelWindow.of(profile.context(), null));
        }
    }

    // The masks are given in the focus items' order, so a window that kept fewer items keeps their masks only.
    private static List<ProtectedMask> masksOf(final Window window, final List<ProtectedMask> masks) {
        final int items = window.focus().items().size();
        return masks.size() > items ? masks.subList(0, items) : masks;
    }

    private List<Block> translateBlocks(
            final CallKind kind, final Window window, final MapLines lines, final RunFrame frame) {
        final List<Block> blocks = new ArrayList<>(frame.system());
        addIfAny(blocks, BlockSlots.MAP, WindowBlocks.map(lines, words));
        addIfAny(blocks, BlockSlots.WINDOW, WindowBlocks.window(window, words));
        translateBlocks(kind, window.focus(), frame, blocks);
        return blocks;
    }

    // Every block at its own language's rate: instructions in English, the rules and the map in the target, the book's
    // text in the source; the template's overhead once.
    private static int promptTokens(final List<Block> blocks, final RunFrame frame, final ModelProfile profile) {
        final List<TokenEstimator.Message> messages = blocks.stream()
                .map(block -> new TokenEstimator.Message(block.text(), languageOf(block, frame)))
                .toList();
        return TokenEstimator.estimateCall(messages, profile.templateOverhead());
    }

    private static @Nullable String languageOf(final Block block, final RunFrame frame) {
        return switch (block.slot()) {
            case BlockSlots.STYLE_SHEET, BlockSlots.LANGUAGE_RULES, BlockSlots.EXAMPLES, BlockSlots.MAP ->
                frame.brief().targetLanguage();
            case BlockSlots.WINDOW, BlockSlots.ITEMS, BlockSlots.SCENE ->
                frame.brief().sourceLanguage();
            default -> INSTRUCTIONS_LANGUAGE;
        };
    }

    private static void addIfAny(final List<Block> blocks, final String slot, final String text) {
        if (!text.isEmpty()) {
            blocks.add(user(slot, text));
        }
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

    private static @Nullable String refusalOf(final FrameKind frameKind, final Focus focus) {
        if (frameKind == FrameKind.NONE) {
            return "This kind of call is not built by the context builder.";
        }
        return frameKind == FrameKind.TRANSLATE && focus.items().isEmpty()
                ? "A translation call needs at least one item."
                : null;
    }

    private static <T> Result<T> invalid(final CallKind kind, final String message) {
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
