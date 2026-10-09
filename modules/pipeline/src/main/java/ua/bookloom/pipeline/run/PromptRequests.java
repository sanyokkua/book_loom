package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchContexts;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * The prompts of one chunk, built the one way a run builds them: the context each draft is shown, the batch that
 * fits the window, the requests of a draft and its repairs, and the inputs of the chunk's reviewer. The job takes its
 * chunk's context, its batches and its loop settings from here, and the prompt evals build their requests through the
 * same methods, so what an eval measures is what a run sends — a hand-made context is how an eval drifts from the app.
 *
 * <p>The reviewer's and the directed fix's requests are built inside their own production classes
 * ({@code ReviewerCall}, {@code DirectedFix}) from the inputs given here, {@link #loopSettings()} and a mask's text.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class PromptRequests {

    // The previous pairs a batch shows; the dial's own count when it is larger.
    private static final int MIN_PAIRS = 2;
    private static final int MAX_PAIRS = 3;

    private final ChunkContext context;
    private final RunSettings settings;
    private final SegmentTranslator translator;
    private final BatchDrafter drafter;
    private final BatchFit fit;

    private PromptRequests(
            final ChunkContext context,
            final RunSettings settings,
            final SegmentTranslator translator,
            final BatchDrafter drafter) {
        this.context = context;
        this.settings = settings;
        this.translator = translator;
        this.drafter = drafter;
        this.fit = new BatchFit(drafter, settings);
    }

    /** A segment that needs a call, with the mask it is shown and what the memory offered it. */
    public record BatchSlot(Segment segment, ProtectedMask mask, TmLookup memory) {

        /** Rejects a missing part. */
        public BatchSlot {
            Objects.requireNonNull(segment, "segment");
            Objects.requireNonNull(mask, "mask");
            Objects.requireNonNull(memory, "memory");
        }
    }

    /**
     * What one batch call is made of: the slots it covers (a prefix of those offered, when the window made it shrink),
     * their items and the context it carries.
     */
    public record PreparedBatch(List<BatchSlot> shown, List<BatchItem> items, BatchContext context) {

        /** Rejects a missing part and copies the lists. */
        public PreparedBatch {
            shown = List.copyOf(Objects.requireNonNull(shown, "shown"));
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            Objects.requireNonNull(context, "context");
        }
    }

    /**
     * Reads the glossary and lexicon for one chunk and hides each of its segments' protected spans.
     *
     * @param glossary the non-null glossary store
     * @param lexicon the non-null lexicon store, read live because a batch of this chunk may add to it
     * @param settings the non-null run settings
     * @param chunk the non-null chunk about to be drafted
     * @param documentGate the non-null gate that checks the document's own markup once the spans are back
     * @param translator the non-null draft step of the run; it is gated through this chunk's spans here
     * @param drafter the non-null batch drafter, whose prompt weighs a batch against the window
     * @return the chunk's prompts, or the glossary's error
     */
    public static Result<PromptRequests> read(
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final RunSettings settings,
            final Chunk chunk,
            final GateFunction documentGate,
            final SegmentTranslator translator,
            final BatchDrafter drafter) {
        Objects.requireNonNull(translator, "translator");
        Objects.requireNonNull(drafter, "drafter");
        return ChunkContext.read(glossary, lexicon, settings, chunk, documentGate)
                .map(read -> new PromptRequests(read, settings, translator.gatedBy(read.gate()), drafter));
    }

    /** The chunk's context, which the job reads its masks, gate and glossary from. */
    ChunkContext context() {
        return context;
    }

    /** The run's draft step, gated through this chunk's protected spans. */
    public SegmentTranslator translator() {
        return translator;
    }

    /** The segment's text with its protected spans hidden, as the model is shown it. */
    public ProtectedMask mask(final Segment segment) {
        return context.mask(segment);
    }

    /** The gate that puts a candidate's protected spans back, then checks the document's own markup. */
    public GateFunction gate() {
        return context.gate();
    }

    /**
     * Assembles what one draft is shown besides its source.
     *
     * @param segment the segment about to be drafted
     * @param earlierMaskedTargets the masked targets of the unit's earlier segments, in document order
     * @param memory what the translation memory offers the segment
     * @param summary the rolling summary's text, or {@code null} while there is none
     * @return the draft's context and its snapshot
     */
    public ContextPackage contextFor(
            final Segment segment,
            final List<String> earlierMaskedTargets,
            final TmLookup memory,
            @Nullable final String summary) {
        return context.contextFor(segment, earlierMaskedTargets, memory, summary);
    }

    /** The request of a segment's first draft, as the draft step sends it. */
    public ChatRequest draftRequest(final Segment segment, final DraftContext draft, final ProtectedMask mask) {
        return translator.requests().draft(segment, draft, mask);
    }

    /** The request of the structural repair a draft whose reply was not the required JSON object is sent. */
    public ChatRequest structuralRepairRequest(
            final Segment segment, final DraftContext draft, final ProtectedMask mask, final String rejectedReply) {
        return translator.requests().structuralRepair(segment, draft, mask, rejectedReply);
    }

    /** The request of the placeholder repair for a reply the gate refuses, or empty when no model repair is needed. */
    public Optional<ChatRequest> placeholderRepairRequest(
            final Segment segment, final DraftContext draft, final ProtectedMask mask, final String rejectedTarget) {
        return translator.requests().placeholderRepair(segment, draft, mask, rejectedTarget);
    }

    /** How many earlier pairs of the unit a batch shows. */
    public int batchPairCount() {
        return Math.min(MAX_PAIRS, Math.max(MIN_PAIRS, settings.dial().precedingTargets()));
    }

    /**
     * Makes the batch of the given slots. It is shown whole when its prompt fits the window; else without the optional
     * context, else with fewer slots, the rest of which batch at their own turn. Fewer than two slots is no batch.
     *
     * @param slots the segments that need a call, in document order, the first being the batch's first
     * @param unit the unit's segments, which decide the next source shown
     * @param earlier the unit's last earlier pairs before the first slot, oldest first
     * @param summary the rolling summary's text, or {@code null}
     * @return the batch, or empty when not even two slots fit
     */
    public Optional<PreparedBatch> batch(
            final List<BatchSlot> slots,
            final List<Segment> unit,
            final List<EarlierPair> earlier,
            @Nullable final String summary) {
        log.debug("Preparing a batch slots={} earlier={} summary={}", slots.size(), earlier.size(), summary != null);
        final List<BatchContext.Pair> pairs = fit.capPairs(earlier.stream()
                .map(e -> new BatchContext.Pair(DisplayText.of(e.segment().masked()), DisplayText.of(e.target())))
                .toList());
        List<BatchSlot> shown = slots;
        while (shown.size() >= BatchDrafter.MIN_BATCHING_SIZE) {
            final PreparedBatch full = prepared(shown, unit, pairs, summary);
            if (fit.fits(full.context(), full.items())) {
                return Optional.of(full);
            }
            final PreparedBatch lean = new PreparedBatch(shown, full.items(), BatchFit.lean(full.context()));
            if (fit.fits(lean.context(), lean.items())) {
                log.debug("Batch of {} fits only without its optional context", shown.size());
                return Optional.of(lean);
            }
            shown = shown.subList(0, shown.size() > BatchDrafter.MIN_BATCHING_SIZE ? (shown.size() + 1) / 2 : 0);
        }
        return Optional.empty();
    }

    /** The request of a batch's one call, which asks for the batch schema, with the key-terms request when it has any. */
    public ChatRequest batchRequest(final PreparedBatch prepared) {
        return drafter.request(prepared.context(), prepared.items());
    }

    /** What the chunk's quality loop and its reviewer are given: the chunk's term pairs and character sheet. */
    public LoopSettings loopSettings() {
        log.debug("Reading the chunk's loop settings project={}", settings.projectId());
        return new LoopSettings(
                settings.mode(),
                settings.dial(),
                settings.frame(),
                settings.names(),
                context.terms(),
                context.termPairs(),
                context.characterLines(),
                context.usualRenderings());
    }

    private PreparedBatch prepared(
            final List<BatchSlot> shown,
            final List<Segment> unit,
            final List<BatchContext.Pair> pairs,
            @Nullable final String summary) {
        final List<BatchItem> items = itemsOf(shown);
        final List<DraftContext> perItem = shown.stream()
                .map(slot -> context.contextFor(slot.segment(), List.of(), slot.memory(), summary)
                        .draftContext())
                .toList();
        final List<String> keyTerms = drafter.keyTermAsks().isAsking()
                ? context.keyTermsIn(shown.stream().map(BatchSlot::segment).toList())
                : List.of();
        final BatchContext batch = new BatchContext(
                merged(items, perItem),
                pairs,
                fit.capNext(BatchFit.nextSourceAfter(unit, shown.getLast().segment())),
                BatchFit.characters(perItem),
                keyTerms);
        return new PreparedBatch(shown, items, batch);
    }

    private static List<BatchItem> itemsOf(final List<BatchSlot> shown) {
        return IntStream.range(0, shown.size())
                .mapToObj(i -> new BatchItem(
                        Integer.toString(i + 1), shown.get(i).mask().maskedText()))
                .toList();
    }

    private static DraftContext merged(final List<BatchItem> items, final List<DraftContext> perItem) {
        return BatchContexts.of(items.stream().map(BatchItem::id).toList(), perItem, List.of(), null, List.of())
                .draft();
    }
}
