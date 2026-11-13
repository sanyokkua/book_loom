package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.memory.ProtectedMask;

/**
 * One run's translate-family planning (design D8): the context builder bound to the run's fixed frame, its model's
 * profile, the window the run latched and the book's reading order. Every draft, batch, format re-ask and repair of a
 * run is planned here, so they share the frame's system bytes and read the same window and map.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class TranslatePlanner {

    private final ContextBuilder builder;
    private final RunFrame frame;
    private final ModelProfile profile;
    private final IntSupplier contextSize;
    private final List<Unit> units;
    private final Supplier<Result<List<ChapterSummary>>> summaries;
    private final BooleanSupplier summariesOn;

    /**
     * Binds the builder to one run.
     *
     * @param builder the non-null context builder
     * @param frame the non-null fixed frame built at run start
     * @param profile the non-null profile of the run's model
     * @param contextSize the window the run latched, read again for each call so a chunk at another window plans for it
     * @param units the non-null units of the book in reading order
     * @param summaries the non-null reader of the project's kept chapter summaries, read again for each draft so a
     *     summary kept mid-run reaches the next chapter's first draft
     * @param summariesOn the non-null reader of the brief's Chapter summaries switch as it is stored now, not as the
     *     frame holds it: the switch may change while the run is paused, and the change applies from the resume
     */
    public TranslatePlanner(
            final ContextBuilder builder,
            final RunFrame frame,
            final ModelProfile profile,
            final IntSupplier contextSize,
            final List<Unit> units,
            final Supplier<Result<List<ChapterSummary>>> summaries,
            final BooleanSupplier summariesOn) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.contextSize = Objects.requireNonNull(contextSize, "contextSize");
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.summariesOn = Objects.requireNonNull(summariesOn, "summariesOn");
    }

    /** The run's fixed frame. */
    public RunFrame frame() {
        return frame;
    }

    /** {@return whether the brief's Chapter summaries switch is on now} */
    public boolean summariesOn() {
        return summariesOn.getAsBoolean();
    }

    /** The profile of the run's model. */
    public ModelProfile profile() {
        return profile;
    }

    /**
     * The book map now, read from the project's glossary and recurring terms and the run's brief.
     *
     * @param entries the non-null glossary entries
     * @param terms the non-null recurring terms
     * @return the map; never null
     */
    public BookMap map(final List<GlossaryEntry> entries, final List<LexiconEntry> terms) {
        return BookMap.of(entries, terms, frame.brief(), units);
    }

    /**
     * The window of a call over the book's reading order.
     *
     * @param focus the non-null items, in reading order
     * @param accepted the non-null reader of the earlier segments' kept targets
     * @return the window, or the reader's error
     */
    public Result<Window> window(final Focus focus, final AcceptedTargets accepted) {
        return WindowAssembler.assemble(units, focus, profile, accepted);
    }

    /**
     * The earlier chapters' summaries a call over {@code focus} carries in block 8, read from the store now. A store
     * that cannot be read gives none, since a draft is better made without the story so far than not made.
     *
     * @param focus the non-null items, in reading order; the first one's chapter decides
     * @return never null; oldest first, empty for a focus with no item, in the first chapter or with the switch off
     */
    public List<EarlierSummary> earlierChapters(final Focus focus) {
        Objects.requireNonNull(focus, "focus");
        if (focus.items().isEmpty()) {
            log.debug("Earlier chapters not read: the call has no item");
            return List.of();
        }
        if (!summariesOn()) {
            log.debug("Earlier chapters not read: the brief's switch is off");
            return List.of();
        }
        final Result<List<ChapterSummary>> stored = summaries.get();
        final List<ChapterSummary> read = stored.data();
        if (read == null) {
            log.warn(
                    "Earlier chapters unreadable code={}; the call carries none",
                    Objects.requireNonNull(stored.error(), "error").code());
            return List.of();
        }
        return EarlierChapters.forFocus(read, frame, focus.items().getFirst().segmentId());
    }

    /**
     * The context of a segment drafted with no neighbours and an empty map, for a draft step given no window.
     *
     * @param segmentId the non-null segment id
     * @param mask the non-null mask of the segment
     * @return the context; never null
     */
    public TranslateContext bare(final String segmentId, final ProtectedMask mask) {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(mask, "mask");
        return new TranslateContext(
                Window.of(focusOf(segmentId, mask.maskedText())),
                BookMap.of(List.of(), List.of(), frame.brief()),
                List.of(),
                Map.of(segmentId, mask),
                List.of());
    }

    /**
     * Plans one translate-family call over a context fixed earlier.
     *
     * @param kind the non-null kind: a draft, a format re-ask or a repair
     * @param context the non-null context the draft was shown
     * @param focus the non-null items this call works on
     * @param extra the non-null block 11 of the call
     * @return the fitted plan, or the builder's error
     */
    public Result<ContextBuilder.FittedPlan> plan(
            final CallKind kind, final TranslateContext context, final Focus focus, final KindExtra extra) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(focus, "focus");
        final int size = contextSize.getAsInt();
        log.debug(
                "Planning {} for {} items summaries={} contextSize={}",
                kind,
                focus.items().size(),
                context.summaries().size(),
                size);
        return builder.planFitted(
                kind,
                context.windowFor(focus),
                context.map(),
                frame,
                profile,
                new CallInputs(
                        context.masksOf(focus.segmentIds()), size, context.memory(), extra, context.summaries()));
    }

    /**
     * Plans one proofread window (design D15 "Call"): the run's frame, the window's map items, its read-only
     * neighbours and its focus with each item's current text. No protected span is hidden, since the current texts
     * already show every locked name as its rendering.
     *
     * @param window the non-null window: the read-only pair before, the focus, the read-only segment after
     * @param map the non-null book map as it stands now
     * @return the fitted plan, or the builder's error
     */
    public Result<ContextBuilder.FittedPlan> planProofread(final Window window, final BookMap map) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(map, "map");
        final int size = contextSize.getAsInt();
        final List<EarlierSummary> earlier = earlierChapters(window.focus());
        log.debug(
                "Planning PROOFREAD for {} items summaries={} contextSize={}",
                window.focus().items().size(),
                earlier.size(),
                size);
        return builder.planFitted(
                CallKind.PROOFREAD,
                window,
                map,
                frame,
                profile,
                new CallInputs(List.of(), size, List.of(), KindExtra.NONE, earlier));
    }

    /**
     * The focus of one item.
     *
     * @param segmentId the non-null segment id
     * @param maskedText the non-null text the model is shown
     * @return the focus; never null
     */
    public static Focus focusOf(final String segmentId, final String maskedText) {
        return new Focus(List.of(new FocusItem(segmentId, maskedText)), Map.of());
    }
}
