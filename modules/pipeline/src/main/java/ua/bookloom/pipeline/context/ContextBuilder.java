package ua.bookloom.pipeline.context;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The one recipe every model call's context comes from (design D8): the run frame's fixed system blocks first, then
 * one last user message with the call's own blocks, the reminder last. A block with nothing to say is left out, so no
 * call carries filler. Every translate-family plan is fitted to the model tier's budgets and capped at its output
 * reserve; block 8 holds the earlier chapters' summaries and the recently-present card. The run's drafts, batches, format re-asks and repairs are all
 * planned here, sized for the window the run latched, the person's own override included.
 */
@Slf4j
public final class ContextBuilder {

    private static final int REPLY_SHARE_DIVISOR = 2;
    private static final String FRAME = "frame";

    private final BlockWords words = BlockWords.bundled();
    private final PromptTemplates templates;
    private final CallBlocks calls;

    /**
     * Builds with the bundled block wording and the catalogue's translate, repair and format re-ask templates.
     *
     * @param templates the non-null prompt catalogue
     */
    @Inject
    public ContextBuilder(final PromptTemplates templates) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.calls = new CallBlocks(templates, words);
    }

    /**
     * Plans one analysis call: the ANALYSIS frame's three system blocks and the task as the one user block. A names,
     * brief or file-name call has no run frame, so it is planned from the frame alone; its context size is the default
     * window, since the model-call seam sizes every call to the window the model was latched at (task 6.9).
     *
     * @param kind the non-null kind of call; an analysis kind
     * @param frame the non-null analysis frame
     * @param task the non-null task text: the instruction, the book's text it reads and the reply's shape
     * @param limit the non-null expected reply length and its cap, which caps the plan's reply
     * @return the plan, or {@link ErrorCode#validation} for a kind that is no analysis call or an empty task
     */
    public Result<PromptPlan> planAnalysis(
            final CallKind kind, final AnalysisFrame frame, final String task, final OutputLimit limit) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(frame, FRAME);
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(limit, "limit");
        log.debug("Planning {} (ANALYSIS frame) taskChars={} cap={}", kind, task.length(), limit.capTokens());
        if (FrameKind.of(kind) != FrameKind.ANALYSIS) {
            return invalid(kind, "This kind of call is not an analysis call.");
        }
        if (task.isBlank()) {
            return invalid(kind, "An analysis call needs a task.");
        }
        final PromptPlan plan = frame.plan(user(BlockSlots.TASK, task.strip()), limit.capTokens());
        PlanLog.plan(kind, new Focus(List.of(), Map.of()), plan);
        return Result.ok(plan);
    }

    /**
     * The request of one analysis call of {@code name}: its task template rendered in the call's frame, planned on the
     * ANALYSIS frame of the frame's source language, as one system and one user message with reasoning off.
     *
     * @param name the non-null prompt of an analysis call
     * @param frame the non-null call frame, whose source language the role names
     * @param values the non-null values of the task template's own slots
     * @param limit the non-null expected reply length and its cap
     * @return the request; never null
     * @throws IllegalArgumentException if {@code name} is not an analysis call, a mistake in the code
     */
    public ChatRequest analysisRequest(
            final PromptName name, final CallFrame frame, final Map<String, String> values, final OutputLimit limit) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(frame, FRAME);
        final String task = templates.renderTask(name, frame, Objects.requireNonNull(values, "values"));
        final Result<PromptPlan> plan =
                planAnalysis(name.callKind(), AnalysisFrame.of(frame.sourceLanguage()), task, limit);
        if (plan.isErr()) {
            throw new IllegalArgumentException(
                    Objects.requireNonNull(plan.error(), "error").message());
        }
        return ChatRequests.build(
                name, ChatRequests.messagesOf(Objects.requireNonNull(plan.data(), "plan")), limit, false);
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
    private record CallWindow(Window window, BookMap map, CallInputs inputs) {}

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
        Objects.requireNonNull(profile, "profile");
        return planFitted(
                kind, window, map, frame, profile, CallInputs.of(masks, ModelWindow.of(profile.context(), null)));
    }

    /**
     * Plans one call's prompt fitted to its budgets for the window a run latched, with what only this call adds.
     *
     * @param kind the non-null kind of call
     * @param window the non-null window
     * @param map the non-null book map
     * @param frame the non-null run frame
     * @param profile the non-null profile of the model the call goes to
     * @param inputs the non-null masks, window size, memory lines and kind extra of the call
     * @return the fitted plan, or the errors {@link #plan(CallKind, Window, BookMap, RunFrame, ModelProfile, List)}
     *     names
     */
    public Result<FittedPlan> planFitted(
            final CallKind kind,
            final Window window,
            final BookMap map,
            final RunFrame frame,
            final ModelProfile profile,
            final CallInputs inputs) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(frame, FRAME);
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(inputs, "inputs");
        final Focus focus = window.focus();
        final FrameKind frameKind = FrameKind.of(kind);
        PlanLog.planning(kind, frameKind, window, inputs);
        final @Nullable String refusal = frameKind.refusal(focus);
        if (refusal != null) {
            return invalid(kind, refusal);
        }
        try {
            return Result.ok(
                    frameKind == FrameKind.TRANSLATE
                            ? translate(kind, new CallWindow(window, map, inputs), frame, profile)
                            : analysis(kind, focus, frame, profile, inputs.contextSize()));
        } catch (RuntimeException cause) {
            log.error("Planning {} for {} items failed", kind, focus.items().size(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal, "Prompt failed", "The call's prompt could not be built.", null, cause));
        }
    }

    // The window, its map lines and the reserve are fitted to the tier's budgets, then rendered once more as sent.
    private FittedPlan translate(
            final CallKind kind, final CallWindow call, final RunFrame frame, final ModelProfile profile) {
        final int contextSize = call.inputs().contextSize();
        final WindowBudgets.Budget budget = new WindowBudgets.Budget(
                WindowBudgets.of(profile.tier()),
                contextSize,
                frame.brief().sourceLanguage(),
                frame.brief().targetLanguage());
        final List<EarlierSummary> earlier = call.inputs().summaries();
        final List<String> summaryLines = earlier.stream()
                .map(summary -> EarlierChapters.line(summary, words))
                .toList();
        final WindowBudgets.Fitted fitted =
                WindowBudgets.fit(budget, call.window(), summaryLines, new PlanSizer(kind, call, frame, profile));
        final PromptPlan plan = new PromptPlan(
                translateBlocks(kind, new Planned(fitted.window(), call, fitted.lines(), fitted.summaries()), frame),
                fitted.promptTokens(),
                fitted.reserve(),
                contextSize,
                EarlierChapters.keptKeys(earlier, fitted.summaries().size()));
        PlanLog.plan(kind, fitted.window().focus(), plan);
        return new FittedPlan(plan, fitted.keptItems());
    }

    private FittedPlan analysis(
            final CallKind kind,
            final Focus focus,
            final RunFrame frame,
            final ModelProfile profile,
            final int contextSize) {
        final List<Block> blocks = new ArrayList<>(frame.analysis());
        if (!focus.items().isEmpty()) {
            blocks.add(user(BlockSlots.ITEMS, calls.items(focus, List.of())));
        }
        final int cap = kind == CallKind.CHAPTER_SUMMARY
                ? OutputLimit.forChapterSummary().capTokens()
                : profile.context().outputMax();
        final PromptPlan plan = new PromptPlan(
                blocks,
                PlanTokens.of(blocks, frame, profile),
                Math.min(cap, contextSize / REPLY_SHARE_DIVISOR),
                contextSize,
                List.of());
        PlanLog.plan(kind, focus, plan);
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
                    masksOf(window, call.inputs().masks()),
                    LexiconFilter.of(frame.brief().sourceLanguage(), target),
                    target);
        }

        @Override
        public int promptTokens(final Window window, final MapLines lines, final List<String> summaries) {
            return PlanTokens.of(
                    translateBlocks(kind, new Planned(window, call, lines, summaries), frame), frame, profile);
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
                    call.inputs().contextSize());
        }
    }

    // The masks are given in the focus items' order, so a window that kept fewer items keeps their masks only.
    private static List<ProtectedMask> masksOf(final Window window, final List<ProtectedMask> masks) {
        final int items = window.focus().items().size();
        return masks.size() > items ? masks.subList(0, items) : masks;
    }

    /**
     * A window as it is rendered: the window, the call it belongs to, its chosen map lines and the earlier chapters'
     * summary lines it keeps.
     */
    private record Planned(Window window, CallWindow call, MapLines lines, List<String> summaries) {}

    private List<Block> translateBlocks(final CallKind kind, final Planned planned, final RunFrame frame) {
        final List<Block> blocks = new ArrayList<>(frame.system());
        final CallInputs inputs = planned.call().inputs();
        addIfAny(blocks, BlockSlots.SCENE, scene(planned, frame));
        addIfAny(blocks, BlockSlots.MAP, WindowBlocks.map(planned.lines(), words));
        addIfAny(blocks, BlockSlots.WINDOW, WindowBlocks.window(planned.window(), inputs.memory(), words));
        addIfAny(
                blocks,
                BlockSlots.KIND_EXTRA,
                calls.kindExtra(kind, planned.window().focus(), inputs.extra()));
        final String target = Objects.requireNonNull(frame.brief().targetLanguage(), "targetLanguage");
        blocks.add(user(BlockSlots.TASK, calls.task(frame.brief().sourceLanguage(), target)));
        blocks.add(user(
                BlockSlots.ITEMS,
                calls.items(
                        planned.window().focus(),
                        Reminder.of(planned.window().focus().items(), frame))));
        return blocks;
    }

    // Block 8: the earlier chapters' summaries, then the card after a blank line. The card is drawn from the window as
    // fitted, so a dropped previous pair moves where the scene starts.
    private String scene(final Planned planned, final RunFrame frame) {
        final String earlier = EarlierChapters.block(planned.summaries(), words);
        final String card = RecentlyPresentCard.of(planned.call().map(), planned.window(), frame);
        return earlier.isEmpty() || card.isEmpty() ? earlier + card : earlier + "\n\n" + card;
    }

    private static void addIfAny(final List<Block> blocks, final String slot, final String text) {
        if (!text.isEmpty()) {
            blocks.add(user(slot, text));
        }
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
}
