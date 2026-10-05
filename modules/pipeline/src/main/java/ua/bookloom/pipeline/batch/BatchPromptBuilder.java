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
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptLanguages;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Renders the batch draft prompt: the static system message (rules, style sheet, language rules,
 * examples — byte-identical across a run's batches) and a user message holding the read-only context blocks, the
 * per-item token lists and the numbered items.
 */
@Slf4j
public final class BatchPromptBuilder {

    private static final Pattern LOCKED_LINE = Pattern.compile("^(?:[A-Za-z0-9_.:-]+: )?⟦g");

    private final PromptTemplates templates;
    private final CallFrame frame;

    /**
     * Creates a builder for one run's templates and call frame.
     *
     * @param templates the non-null loaded templates
     * @param frame the non-null run's languages, style sheet and policy
     */
    public BatchPromptBuilder(final PromptTemplates templates, final CallFrame frame) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.frame = Objects.requireNonNull(frame, "frame");
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
                "Building batch prompt items={} precedingPairs={} nextSource={} characters={}",
                items.size(),
                context.precedingPairs().size(),
                context.nextSource() != null,
                context.characters().size());
        final String system =
                templates.renderSystem(PromptName.DRAFT_BATCH_JSON, frame).strip();
        final String user = templates
                .renderUser(PromptName.DRAFT_BATCH_JSON, userValues(context, items))
                .strip();
        if (log.isTraceEnabled()) {
            log.trace("Batch prompt system={} user={}", system, user);
        }
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    /**
     * Builds the request for a batch.
     *
     * @param messages the messages from {@link #messagesFor}
     * @param items the batch items, to size the reply's cap
     * @param lowerTemperature whether a retry asks for the lower temperature
     * @return the request, asking for the batch schema
     */
    public ChatRequest requestFor(
            final List<ChatMessage> messages, final List<BatchItem> items, final boolean lowerTemperature) {
        final OutputLimit limit = OutputLimit.forBatch(
                items.stream().map(BatchItem::masked).toList(), frame.sourceLanguage(), frame.targetLanguage());
        return ChatRequests.build(PromptName.DRAFT_BATCH_JSON, messages, limit, lowerTemperature);
    }

    private Map<String, String> userValues(final BatchContext context, final List<BatchItem> items) {
        final DraftContext draft = context.draft();
        final Map<String, String> values = new HashMap<>();
        values.put("source", PromptLanguages.describe(frame.sourceLanguage()));
        values.put("target", PromptLanguages.describe(frame.targetLanguage()));
        values.put("items", numbered(items));
        values.put("itemTokens", itemTokens(items));
        values.put("summary", draft.summary() == null ? "" : draft.summary());
        values.put("glossaryTerms", lines(draft.glossaryLines(), false));
        values.put("lockedNames", lines(draft.glossaryLines(), true));
        values.put("suggestedTerms", String.join("\n", draft.suggestedLines()));
        values.put("memoryHint", String.join("\n", draft.memoryLines()));
        values.put("lexiconTerms", String.join("\n", draft.lexiconLines()));
        values.put("keyTerms", String.join(", ", context.keyTerms()));
        values.put("characters", String.join("\n", context.characters()));
        values.put("precedingPairs", pairs(context.precedingPairs()));
        values.put("nextSource", context.nextSource() == null ? "" : context.nextSource());
        return values;
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
