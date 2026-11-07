package ua.bookloom.pipeline.proofread;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.context.Block;
import ua.bookloom.pipeline.context.BlockSlots;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.FocusItem;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.context.WindowPair;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * One proofread window's call (design D15 "Call", "Reply"): planned on the run's TRANSLATE frame, so its system part is
 * the drafts' byte for byte, with the window's map items, its read-only neighbours and each focus item's source,
 * current text and reasons; sent to the run's own model through the run's one call seam. The reply is read as
 * {@code {"unchanged":true}} or as the changed items: an id outside the focus and a text equal to the current one are
 * dropped, and a focus id the reply leaves out is unchanged.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class ProofreadCall {

    private static final String ITEMS = "items";

    private final TranslatePlanner planner;
    private final ModelCalls calls;
    private final ObjectMapper mapper;

    /**
     * The texts a window shows, by segment id, its neighbours' included.
     *
     * @param sources each segment's masked source
     * @param currents each segment's decided text, document tokens in place
     */
    public record Texts(Map<String, String> sources, Map<String, String> currents) {

        /** Copies the maps. */
        public Texts {
            sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
            currents = Map.copyOf(Objects.requireNonNull(currents, "currents"));
        }

        String source(final String id) {
            return Objects.requireNonNull(sources.get(id), () -> "no source for " + id);
        }

        String current(final String id) {
            return Objects.requireNonNull(currents.get(id), () -> "no text for " + id);
        }
    }

    /**
     * What a window's reply said.
     *
     * @param readIds the focus segments the call read, in reading order: the whole focus unless the budgets cut it
     * @param changes the changed texts by segment id, each a focus id whose text differs from the current one
     * @param readable whether the reply could be read at all; an unreadable one changes nothing and stores no verdict
     */
    public record Answer(List<String> readIds, Map<String, String> changes, boolean readable) {

        /** Copies the parts. */
        public Answer {
            readIds = List.copyOf(Objects.requireNonNull(readIds, "readIds"));
            changes = Map.copyOf(Objects.requireNonNull(changes, "changes"));
        }
    }

    /**
     * Binds the call to one run.
     *
     * @param planner the non-null planner bound to the run's frame and model
     * @param calls the non-null seam every call of the run goes through
     * @param mapper the non-null JSON reader
     */
    public ProofreadCall(final TranslatePlanner planner, final ModelCalls calls, final ObjectMapper mapper) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * One window's call as planned, before it is sent: what the context builder fitted and which focus items it kept.
     *
     * @param window the window
     * @param fitted the fitted plan
     */
    public record Planned(ProofreadWindow window, ContextBuilder.FittedPlan fitted) {

        /** Rejects a missing part. */
        public Planned {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(fitted, "fitted");
        }

        /** {@return the focus segments the call reads, in reading order: the whole focus unless the budgets cut it} */
        List<String> readIds() {
            return window.focusIds().subList(0, fitted.keptItems());
        }

        /** {@return the map lines the call carries, as sent; empty when it carries none} */
        String mapLines() {
            return fitted.plan().blocks().stream()
                    .filter(block -> BlockSlots.MAP.equals(block.slot()))
                    .map(Block::text)
                    .findFirst()
                    .orElse("");
        }
    }

    /** {@return the announcer of the run's steps, which a window's step is announced through} */
    StepAnnouncer steps() {
        return calls.steps();
    }

    /**
     * Reads one window: plans it and sends it.
     *
     * @param window the non-null window
     * @param texts the non-null texts of the window's segments
     * @param map the non-null book map as it stands now
     * @return the answer, or the plan's or the call's error, which the caller routes like a draft's
     */
    public Result<Answer> read(final ProofreadWindow window, final Texts texts, final BookMap map) {
        return plan(window, texts, map).flatMap(planned -> send(planned, texts));
    }

    /**
     * Plans one window's call with no model call, so its fingerprint can be taken from the map lines it would carry.
     *
     * @param window the non-null window
     * @param texts the non-null texts of the window's segments
     * @param map the non-null book map as it stands now
     * @return the planned call, or the plan's error
     */
    public Result<Planned> plan(final ProofreadWindow window, final Texts texts, final BookMap map) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(texts, "texts");
        Objects.requireNonNull(map, "map");
        log.debug(
                "Proofread call planned window={} focus={} before={} after={}",
                window.label(),
                window.focusIds().size(),
                window.beforeId() != null,
                window.afterId() != null);
        return planner.planProofread(contextWindow(window, texts), map).map(fitted -> new Planned(window, fitted));
    }

    /**
     * Sends a planned window's call.
     *
     * @param planned the non-null planned call
     * @param texts the non-null texts it was planned with
     * @return the answer, or the call's error
     */
    public Result<Answer> send(final Planned planned, final Texts texts) {
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(texts, "texts");
        final List<String> readIds = planned.readIds();
        final String target = Objects.requireNonNull(planner.frame().brief().targetLanguage(), "targetLanguage");
        final OutputLimit limit = OutputLimit.forBatch(
                readIds.stream().map(texts::source).toList(),
                planner.frame().brief().sourceLanguage(),
                target);
        final ChatRequest request =
                ChatRequests.build(PromptName.PROOFREAD, planned.fitted().plan(), limit, false);
        if (log.isTraceEnabled()) {
            log.trace("Proofread prompt ids={} messages={}", readIds, request.messages());
        }
        return calls.callAbout(CallKind.PROOFREAD, readIds, request).map(response -> answer(readIds, texts, response));
    }

    private Answer answer(final List<String> readIds, final Texts texts, final ChatResponse response) {
        log.trace("Proofread reply ids={} reply={}", readIds, response.content());
        final Optional<JsonNode> root = JsonReplies.tolerant(mapper, response.content());
        if (root.isEmpty() || !root.get().isObject()) {
            log.warn("Proofread reply unreadable ids={}; the window changes nothing", readIds);
            return new Answer(readIds, Map.of(), false);
        }
        final JsonNode items = root.get().path(ITEMS);
        if (root.get().path("unchanged").asBoolean(false) || !items.isArray()) {
            log.debug("Proofread reply unchanged ids={}", readIds.size());
            return new Answer(readIds, Map.of(), true);
        }
        final Map<String, String> changes = new LinkedHashMap<>();
        items.forEach(item -> changed(item, readIds, texts)
                .ifPresent(id -> changes.put(id, item.path("target").asText())));
        log.debug("Proofread reply ids={} items={} changes={}", readIds.size(), items.size(), changes.size());
        return new Answer(readIds, changes, true);
    }

    // An item counts only for a focus id the call read and a text that differs from the current one.
    private static Optional<String> changed(final JsonNode item, final List<String> readIds, final Texts texts) {
        final String id = item.path("id").asText("");
        final @Nullable JsonNode target = item.get("target");
        if (!readIds.contains(id) || target == null || !target.isTextual()) {
            log.debug("Proofread item dropped id={}: not a focus id or no text", id);
            return Optional.empty();
        }
        if (target.asText().strip().equals(texts.current(id).strip())) {
            log.debug("Proofread item dropped id={}: the text is the current one", id);
            return Optional.empty();
        }
        return Optional.of(id);
    }

    private static Window contextWindow(final ProofreadWindow window, final Texts texts) {
        final List<FocusItem> items = new ArrayList<>();
        window.focusIds().forEach(id -> items.add(new FocusItem(id, texts.source(id), texts.current(id))));
        final @Nullable String before = window.beforeId();
        final List<WindowPair> previous = before == null
                ? List.of()
                : List.of(new WindowPair(before, texts.source(before), texts.current(before)));
        final @Nullable String after = window.afterId();
        return new Window(
                previous,
                new Focus(items, window.reasons()),
                after == null ? null : texts.source(after),
                after == null ? null : texts.current(after));
    }
}
