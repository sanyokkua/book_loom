package ua.bookloom.pipeline.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.AcceptedTargets;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.FocusItem;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TmLookup;

/**
 * The prompts of one chunk, built the one way a run builds them: the context each draft is shown, the batch that
 * fits the window, the requests of a draft, its format re-ask and its repair, and the inputs of the chunk's reviewer.
 * Every translate-family request is planned by the run's context builder over the window the book's reading order
 * gives and the book map as it stands. The job takes its chunk's context, its batches and its loop
 * settings from here, and the prompt evals build their requests through the same methods, so what an eval measures is
 * what a run sends — a hand-made context is how an eval drifts from the app.
 *
 * <p>The reviewer's and the directed fix's requests are built inside their own production classes
 * ({@code ReviewerCall}, {@code DirectedFix}) from the inputs given here, {@link #loopSettings()} and a mask's text.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class PromptRequests {

    private final ChunkContext context;
    private final RunSettings settings;
    private final SegmentTranslator translator;
    private final BatchDrafter drafter;
    private final TranslatePlanner planner;

    private PromptRequests(
            final ChunkContext context,
            final RunSettings settings,
            final SegmentTranslator translator,
            final BatchDrafter drafter,
            final TranslatePlanner planner) {
        this.context = context;
        this.settings = settings;
        this.translator = translator;
        this.drafter = drafter;
        this.planner = planner;
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
     * their items, the context they are shown and the plan the call sends.
     */
    public record PreparedBatch(
            List<BatchSlot> shown, List<BatchItem> items, TranslateContext context, PromptPlan plan) {

        /** Rejects a missing part and copies the lists. */
        public PreparedBatch {
            shown = List.copyOf(Objects.requireNonNull(shown, "shown"));
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(plan, "plan");
        }

        /** The segment ids of the shown slots, in order. */
        public List<String> segmentIds() {
            return shown.stream().map(slot -> slot.segment().id()).toList();
        }
    }

    /**
     * Reads the glossary and lexicon for one chunk, hides each of its segments' protected spans and reads the book map.
     *
     * @param glossary the non-null glossary store
     * @param lexicon the non-null lexicon store, read live because a batch of this chunk may add to it
     * @param settings the non-null run settings
     * @param chunk the non-null chunk about to be drafted
     * @param documentGate the non-null gate that checks the document's own markup once the spans are back
     * @param translator the non-null draft step of the run; it is gated through this chunk's spans here
     * @param drafter the non-null batch drafter
     * @param planner the non-null planner of the run's translate-family calls
     * @return the chunk's prompts, or the glossary's error
     */
    public static Result<PromptRequests> read(
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final RunSettings settings,
            final Chunk chunk,
            final GateFunction documentGate,
            final SegmentTranslator translator,
            final BatchDrafter drafter,
            final TranslatePlanner planner) {
        Objects.requireNonNull(translator, "translator");
        Objects.requireNonNull(drafter, "drafter");
        Objects.requireNonNull(planner, "planner");
        return ChunkContext.read(glossary, lexicon, settings, chunk, documentGate)
                .map(read -> new PromptRequests(read, settings, translator.gatedBy(read.gate()), drafter, planner));
    }

    /** The chunk's context, which the job reads its masks, gate and glossary from. */
    ChunkContext context() {
        return context;
    }

    /** The run's draft step, gated through this chunk's protected spans. */
    public SegmentTranslator translator() {
        return translator;
    }

    /**
     * The book map now: the glossary as it stood when the chunk started, and the recurring terms as they stand, since
     * a batch of this chunk may have established a rendering a later draft must keep.
     */
    public BookMap map() {
        final BookMap map = planner.map(context.glossary(), context.lexicon().entries(settings.projectId()));
        log.debug(
                "Book map entries={} terms={} glossed={}",
                map.entries().size(),
                map.terms().size(),
                map.glossFirstUse().size());
        return map;
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
     * Assembles what the old assembler gives one draft besides its source; a run reads only the memory's lines of it.
     * The rolling summary no longer reaches a draft: the recently-present card says who is who (design D10).
     *
     * @param segment the segment about to be drafted
     * @param earlierMaskedTargets the masked targets of the unit's earlier segments, in document order
     * @param memory what the translation memory offers the segment
     * @return the draft's context and the items it was built from
     */
    public ContextPackage contextFor(
            final Segment segment, final List<String> earlierMaskedTargets, final TmLookup memory) {
        return context.contextFor(segment, earlierMaskedTargets, memory, null);
    }

    /**
     * The window, map and memory one or more consecutive segments are drafted with.
     *
     * @param slots the non-null segments, in reading order, with their masks and memory offers
     * @param accepted the non-null reader of the earlier segments' kept targets
     * @param keyTerms the non-null recurring key terms the call asks renderings for; empty when it asks none
     * @return the context, or the reader's error
     */
    public Result<TranslateContext> translateContext(
            final List<BatchSlot> slots, final AcceptedTargets accepted, final List<String> keyTerms) {
        Objects.requireNonNull(slots, "slots");
        final Focus focus = new Focus(
                slots.stream()
                        .map(slot ->
                                new FocusItem(slot.segment().id(), slot.mask().maskedText()))
                        .toList(),
                Map.of());
        final Map<String, ProtectedMask> masks = new LinkedHashMap<>();
        slots.forEach(slot -> masks.put(slot.segment().id(), slot.mask()));
        final List<String> memory = slots.stream()
                .flatMap(slot ->
                        contextFor(slot.segment(), List.of(), slot.memory()).draftContext().memoryLines().stream())
                .toList();
        return planner.window(focus, accepted)
                .map(window -> new TranslateContext(window, map(), memory, masks, keyTerms));
    }

    /**
     * The user message's sections of a segment's first draft over {@code window}, planned the way the draft step plans
     * it; the planning is deterministic, so they are the sections the draft was sent. A segment drafted in pieces is
     * described by the plan of its whole text.
     *
     * @param segment the non-null segment drafted alone
     * @param window the non-null window, map and memory the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @return the sections in the order sent, the system message's left out, or the builder's error
     */
    public Result<List<PromptSection>> sentSections(
            final Segment segment, final TranslateContext window, final ProtectedMask mask) {
        return sentSections(segment, window, mask, KindExtra.NONE);
    }

    /**
     * As {@link #sentSections(Segment, TranslateContext, ProtectedMask)}, for a draft that carries block 11: a review
     * retry's note.
     *
     * @param extra the non-null block 11 the draft is sent with
     */
    public Result<List<PromptSection>> sentSections(
            final Segment segment, final TranslateContext window, final ProtectedMask mask, final KindExtra extra) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(extra, "extra");
        return planner.plan(CallKind.DRAFT, window, TranslatePlanner.focusOf(segment.id(), mask.maskedText()), extra)
                .map(fitted -> userSections(fitted.plan().sections()));
    }

    /**
     * The sections of the user message alone: the system message's are the run's fixed frame, stored once.
     *
     * @param sections the non-null sections of one call
     * @return never null; the user-origin ones, in order
     */
    static List<PromptSection> userSections(final List<PromptSection> sections) {
        return sections.stream()
                .filter(section -> section.origin() == PromptSection.Origin.USER)
                .toList();
    }

    /** The request of a segment's first draft, as the draft step sends it. */
    public ChatRequest draftRequest(final Segment segment, final TranslateContext draft, final ProtectedMask mask) {
        return translator.requests().draft(segment, draft, mask);
    }

    /** The request of the one format re-ask a draft whose reply could not be read is sent. */
    public ChatRequest formatReaskRequest(
            final Segment segment, final TranslateContext draft, final ProtectedMask mask, final String rejectedReply) {
        return translator.requests().formatReask(segment, draft, mask, rejectedReply);
    }

    /** The one repair's request for a draft whose text {@code previous} still holds {@code defects}. */
    public ChatRequest repairRequest(
            final Segment segment,
            final TranslateContext draft,
            final ProtectedMask mask,
            final String previous,
            final List<QaFinding> defects) {
        return translator.requests().repair(segment, draft, mask, previous, defects);
    }

    /** The one repair's request for a reply whose marks the code cannot put back, or empty when no call is needed. */
    public Optional<ChatRequest> repairRequest(
            final Segment segment,
            final TranslateContext draft,
            final ProtectedMask mask,
            final String rejectedTarget) {
        return translator.requests().repairOfLostMarks(segment, draft, mask, rejectedTarget);
    }

    /**
     * Makes the batch of the given slots, fitted to the model tier's budgets: what helps least is dropped first, and
     * only then batch items, the rest of which batch at their own turn. Fewer than two slots is no batch.
     *
     * @param slots the segments that need a call, in document order, the first being the batch's first
     * @param accepted the non-null reader of the earlier segments' kept targets
     * @return the batch, empty when not even two slots fit, or the reader's or the builder's error
     */
    public Result<Optional<PreparedBatch>> batch(final List<BatchSlot> slots, final AcceptedTargets accepted) {
        log.debug("Preparing a batch slots={}", slots.size());
        final Result<Optional<PreparedBatch>> whole = prepared(slots, accepted);
        final Optional<PreparedBatch> batch = whole.data();
        if (batch == null || batch.isEmpty() || batch.get().shown().size() == slots.size()) {
            return whole;
        }
        // The window kept fewer items, so the next source is the first one left out: the shown prefix is planned again.
        log.debug(
                "Batch kept {} of {} slots; planned again for them",
                batch.get().shown().size(),
                slots.size());
        return prepared(batch.get().shown(), accepted);
    }

    private Result<Optional<PreparedBatch>> prepared(final List<BatchSlot> slots, final AcceptedTargets accepted) {
        final List<String> keyTerms = drafter.keyTermAsks().isAsking()
                ? context.keyTermsIn(slots.stream().map(BatchSlot::segment).toList())
                : List.of();
        final Result<TranslateContext> read = translateContext(slots, accepted, keyTerms);
        final TranslateContext shown = read.data();
        if (shown == null) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        final List<BatchItem> items = itemsOf(slots);
        final List<String> ids = slots.stream().map(slot -> slot.segment().id()).toList();
        return drafter.plan(shown, items, ids).map(fitted -> {
            final int kept = fitted.keptItems();
            if (kept < BatchDrafter.MIN_BATCHING_SIZE) {
                log.debug("No batch fits the window slots={} kept={}", slots.size(), kept);
                return Optional.empty();
            }
            return Optional.of(new PreparedBatch(slots.subList(0, kept), items.subList(0, kept), shown, fitted.plan()));
        });
    }

    /** The request of a batch's one call, which asks for the batch schema, with the key-terms request when it has any. */
    public ChatRequest batchRequest(final PreparedBatch prepared) {
        return drafter.request(prepared.plan(), prepared.items());
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
                        context.reviewerSheets())
                .withPinnedTargets(context.pinnedTargets())
                .withNames(context.names());
    }

    private static List<BatchItem> itemsOf(final List<BatchSlot> shown) {
        return IntStream.range(0, shown.size())
                .mapToObj(i -> new BatchItem(
                        Integer.toString(i + 1), shown.get(i).mask().maskedText()))
                .toList();
    }
}
