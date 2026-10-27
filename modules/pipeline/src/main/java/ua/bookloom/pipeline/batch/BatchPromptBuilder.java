package ua.bookloom.pipeline.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.prompt.BatchNotesSwitch;
import ua.bookloom.pipeline.prompt.BatchSchema;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptLanguages;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Renders the batch draft prompt: the static system message (rules, style sheet, language rules,
 * examples — byte-identical across a run's batches) and a user message holding the read-only context blocks, the
 * per-item token lists and the items: numbered ({@link BatchFormat#JSON}), as one marked passage
 * ({@link BatchFormat#MARKERS}) or numbered beside the passage they make ({@link BatchFormat#DUAL}).
 */
@Slf4j
public final class BatchPromptBuilder {

    /** Room for one note of the notes arm: a short line naming a hard phrase and how it will be rendered. */
    private static final int NOTE_TOKENS_PER_ITEM = 32;

    private static final Pattern LOCKED_LINE = Pattern.compile("^(?:[A-Za-z0-9_.:-]+: )?⟦g");

    private final PromptTemplates templates;
    private final CallFrame frame;
    private final BatchFormat format;
    private final boolean relatedPairs;

    /**
     * Creates a builder for one run's templates, call frame and batch format.
     *
     * @param templates the non-null loaded templates
     * @param frame the non-null run's languages, style sheet and policy
     * @param format the non-null format batches are sent and answered in
     * @param relatedPairs whether this run's batches carry the related-earlier-pairs block, which the system rules
     *     then name as context; off, the system prompt reads as it did before the block existed
     */
    public BatchPromptBuilder(
            final PromptTemplates templates,
            final CallFrame frame,
            final BatchFormat format,
            final boolean relatedPairs) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.format = Objects.requireNonNull(format, "format");
        this.relatedPairs = relatedPairs;
    }

    /** The format this builder's batches are sent and answered in. */
    public BatchFormat format() {
        return format;
    }

    /**
     * Builds the system and user message of one batch.
     *
     * @param context the non-null read-only context
     * @param items the non-null batch items, at least one
     * @return the two messages
     */
    public List<ChatMessage> messagesFor(final BatchContext context, final List<BatchItem> items) {
        Objects.requireNonNull(context, "context");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("a batch needs at least one item");
        }
        log.debug(
                "Building batch prompt format={} items={} precedingPairs={} nextSource={} characters={}",
                format,
                items.size(),
                context.precedingPairs().size(),
                context.nextSource() != null,
                context.characters().size());
        final String system = templates
                .renderSystem(format.promptName(), frame, systemGates(items))
                .strip();
        final String user = templates
                .renderUser(format.promptName(), userValues(context, items))
                .strip();
        if (log.isTraceEnabled()) {
            log.trace("Batch prompt system={} user={}", system, user);
        }
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    /**
     * The parts of one batch's prompt as {@link #messagesFor} sends them, in order.
     *
     * @param context the non-null read-only context
     * @param items the non-null batch items, at least one
     * @return never null; the filled parts, the system message's first
     */
    public List<PromptSection> sectionsFor(final BatchContext context, final List<BatchItem> items) {
        Objects.requireNonNull(context, "context");
        return templates.sectionsOf(format.promptName(), frame, systemGates(items), userValues(context, items));
    }

    /**
     * Builds the request for a batch.
     *
     * @param messages the messages from {@link #messagesFor}
     * @param items the batch items, to size the reply's cap
     * @param lowerTemperature whether a retry asks for the lower temperature
     * @return the request, asking for the batch schema unless the format answers in plain text
     */
    public ChatRequest requestFor(
            final List<ChatMessage> messages, final List<BatchItem> items, final boolean lowerTemperature) {
        final boolean notes = notesOn();
        final OutputLimit sized = OutputLimit.forBatch(
                items.stream().map(BatchItem::masked).toList(), frame.sourceLanguage(), frame.targetLanguage());
        final OutputLimit limit = notes ? withNotes(sized, items.size()) : sized;
        final ChatRequest request =
                ChatRequests.build(format.promptName(), messages, limit, lowerTemperature, frame.requestDefaults());
        log.debug("Batch request items={} notes={} cap={}", items.size(), notes, limit.capTokens());
        return notes ? withNotesSchema(request) : request;
    }

    // The marked arm answers in plain text, so it has no entry to carry a note.
    private boolean notesOn() {
        return format != BatchFormat.MARKERS && BatchNotesSwitch.isOn();
    }

    private Map<String, String> systemGates(final List<BatchItem> items) {
        final Map<String, String> gates = new HashMap<>(PromptTemplates.tokenGate(hasTokens(items)));
        gates.putAll(PromptTemplates.notesGate(notesOn()));
        gates.putAll(PromptTemplates.relatedPairsGate(relatedPairs));
        return gates;
    }

    /** Each note is a short line the reply carries besides the translation, so the output limit grows by one per item. */
    private static OutputLimit withNotes(final OutputLimit limit, final int itemCount) {
        final int room = NOTE_TOKENS_PER_ITEM * itemCount;
        return new OutputLimit(limit.expectedTokens() + room, limit.capTokens() + room);
    }

    private ChatRequest withNotesSchema(final ChatRequest request) {
        return new ChatRequest(
                request.messages(),
                request.temperature(),
                new ResponseFormat(format.promptName().responseFormatName(), BatchSchema.SCHEMA_WITH_NOTES),
                request.reasoningEnabled(),
                request.contextWindow(),
                request.expectedOutputTokens(),
                request.maxOutputTokens(),
                request.seed(),
                request.callKind(),
                request.sampling());
    }

    private static boolean hasTokens(final List<BatchItem> items) {
        return items.stream().anyMatch(item -> !Tokens.inOrder(item.masked()).isEmpty());
    }

    private Map<String, String> userValues(final BatchContext context, final List<BatchItem> items) {
        final DraftContext draft = context.draft();
        final Map<String, String> values = new HashMap<>();
        values.put("source", PromptLanguages.describe(frame.sourceLanguage()));
        values.put("target", PromptLanguages.describe(frame.targetLanguage()));
        values.put("items", format == BatchFormat.MARKERS ? passage(items) : numbered(items));
        if (format == BatchFormat.DUAL) {
            values.put(
                    "passage",
                    String.join("\n\n", items.stream().map(BatchItem::masked).toList()));
        }
        values.put("itemTokens", itemTokens(items));
        values.put("summary", draft.summary() == null ? "" : draft.summary());
        values.put("glossaryTerms", lines(draft.glossaryLines(), false));
        values.put("lockedNames", lines(draft.glossaryLines(), true));
        values.put("suggestedTerms", String.join("\n", draft.suggestedLines()));
        values.put("memoryHint", String.join("\n", draft.memoryLines()));
        values.put("lexiconTerms", String.join("\n", draft.lexiconLines()));
        if (format != BatchFormat.MARKERS) {
            values.put("keyTerms", String.join(", ", context.keyTerms()));
        }
        values.put("characters", String.join("\n", context.characters()));
        values.put("relevantPairs", pairs(context.relevantPairs()));
        values.put("precedingPairs", pairs(context.precedingPairs()));
        values.put("nextSource", context.nextSource() == null ? "" : context.nextSource());
        return values;
    }

    private static String passage(final List<BatchItem> items) {
        return String.join(
                "\n\n",
                items.stream()
                        .map(item -> BatchMarkers.of(item.id()) + " " + item.masked())
                        .toList());
    }

    private static String numbered(final List<BatchItem> items) {
        return String.join(
                "\n",
                items.stream()
                        .map(item -> "<s id=\"" + item.id() + "\">" + item.masked() + "</s>")
                        .toList());
    }

    /** One line per item that holds tokens; an item with none is named by the rule instead, so no line says "none". */
    private static String itemTokens(final List<BatchItem> items) {
        return String.join(
                "\n",
                items.stream()
                        .filter(item -> !Tokens.inOrder(item.masked()).isEmpty())
                        .map(item -> item.id() + ": " + String.join(" ", Tokens.inOrder(item.masked())))
                        .toList());
    }

    private static String pairs(final List<BatchContext.Pair> pairs) {
        return String.join(
                "\n\n",
                pairs.stream()
                        .map(pair -> "Source: " + pair.source() + "\nTranslation: " + pair.target())
                        .toList());
    }

    /**
     * The glossary lines that name a locked token, or the others. Tokens are numbered per segment, so one batch holds
     * several different {@code ⟦g0⟧}: the caller prefixes a locked line with its item id ({@code 3: ⟦g0⟧ → name}).
     */
    private static String lines(final List<String> lines, final boolean locked) {
        return String.join(
                "\n",
                lines.stream()
                        .filter(line -> LOCKED_LINE.matcher(line).find() == locked)
                        .toList());
    }
}
