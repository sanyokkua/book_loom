package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
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

    /**
     * Binds the builder to one run.
     *
     * @param builder the non-null context builder
     * @param frame the non-null fixed frame built at run start
     * @param profile the non-null profile of the run's model
     * @param contextSize the window the run latched, read again for each call so a chunk at another window plans for it
     * @param units the non-null units of the book in reading order
     */
    public TranslatePlanner(
            final ContextBuilder builder,
            final RunFrame frame,
            final ModelProfile profile,
            final IntSupplier contextSize,
            final List<Unit> units) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.contextSize = Objects.requireNonNull(contextSize, "contextSize");
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
    }

    /** The run's fixed frame. */
    public RunFrame frame() {
        return frame;
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
        final List<String> keyTerms = kind == CallKind.DRAFT ? context.keyTerms() : List.of();
        final int size = contextSize.getAsInt();
        log.debug(
                "Planning {} for {} items keyTerms={} contextSize={}",
                kind,
                focus.items().size(),
                keyTerms.size(),
                size);
        return builder.planFitted(
                kind,
                context.windowFor(focus),
                context.map(),
                frame,
                profile,
                new CallInputs(context.masksOf(focus.segmentIds()), size, context.memory(), keyTerms, extra));
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
