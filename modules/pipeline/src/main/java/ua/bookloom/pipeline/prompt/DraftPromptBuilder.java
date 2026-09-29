package ua.bookloom.pipeline.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.pipeline.Tokens;

/** Renders the catalog's one-segment draft-translation prompt with the currently available context. */
@Slf4j
public final class DraftPromptBuilder {

    private final PromptTemplates templates;
    private final CallFrame frame;

    /** Creates a builder for one job's templates and its call frame: languages, style sheet and policy. */
    public DraftPromptBuilder(final PromptTemplates templates, final CallFrame frame) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.frame = Objects.requireNonNull(frame, "frame");
    }

    /** The job's source language tag, or null when it is inferred from the text. */
    public @Nullable String sourceLanguage() {
        return frame.sourceLanguage();
    }

    /** The job's target language tag. */
    public String targetLanguage() {
        return frame.targetLanguage();
    }

    /** Builds the catalog system and user messages for one masked segment. */
    public List<ChatMessage> messagesFor(final Segment segment) {
        return messagesFor(segment, DraftContext.empty());
    }

    /** Builds the catalog system and user messages for one masked segment with its available prior targets. */
    public List<ChatMessage> messagesFor(final Segment segment, final DraftContext context) {
        Objects.requireNonNull(segment, "segment");
        return messagesFor(segment, context, segment.masked());
    }

    /**
     * Builds the catalog system and user messages, showing the model {@code shownText} instead of the segment's own
     * masked text — the text with its protected spans hidden behind tokens.
     *
     * @param shownText the text the model translates and whose tokens it must copy; never null
     */
    public List<ChatMessage> messagesFor(final Segment segment, final DraftContext context, final String shownText) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shownText, "shownText");
        final String source = PromptLanguages.describe(frame.sourceLanguage());
        final String target = PromptLanguages.describe(frame.targetLanguage());
        log.debug(
                "Building draft prompt sourceLanguage={} targetLanguage={} segmentId={} shownLength={}",
                source,
                target,
                segment.id(),
                shownText.length());
        final String system = systemMessage();
        final String user = userMessage(source, target, shownText, context);
        if (log.isTraceEnabled()) {
            log.trace("Draft prompt system={} user={}", system, user);
        }
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    /** Builds a correction request after a reply does not match the required response object. */
    public List<ChatMessage> messagesForStructuredRepair(
            final Segment segment, final DraftContext context, final String rejectedReply, final String diagnostic) {
        Objects.requireNonNull(segment, "segment");
        return messagesForStructuredRepair(segment, context, segment.masked(), rejectedReply, diagnostic);
    }

    /** As {@link #messagesForStructuredRepair(Segment, DraftContext, String, String)}, over {@code shownText}. */
    public List<ChatMessage> messagesForStructuredRepair(
            final Segment segment,
            final DraftContext context,
            final String shownText,
            final String rejectedReply,
            final String diagnostic) {
        Objects.requireNonNull(rejectedReply, "rejectedReply");
        Objects.requireNonNull(diagnostic, "diagnostic");
        final String correction = templates.renderUser(
                PromptName.STRUCTURAL_REPAIR, Map.of("rejectedReply", rejectedReply, "diagnostic", diagnostic));
        return withCorrection(segment, context, shownText, correction);
    }

    /**
     * Builds a correction request after a structurally valid target fails the document placeholder hard gate.
     *
     * @param gateNote the rule the gate refused, so the model is told which rule broke as well as which tokens are
     *     required; {@code null} to name none
     */
    public List<ChatMessage> messagesForPlaceholderRepair(
            final Segment segment,
            final DraftContext context,
            final String rejectedTarget,
            @Nullable final String gateNote) {
        Objects.requireNonNull(segment, "segment");
        return messagesForPlaceholderRepair(segment, context, segment.masked(), rejectedTarget, gateNote);
    }

    /** As {@link #messagesForPlaceholderRepair(Segment, DraftContext, String, String)}, over {@code shownText}. */
    public List<ChatMessage> messagesForPlaceholderRepair(
            final Segment segment,
            final DraftContext context,
            final String shownText,
            final String rejectedTarget,
            @Nullable final String gateNote) {
        Objects.requireNonNull(rejectedTarget, "rejectedTarget");
        final Map<String, String> values = new HashMap<>();
        values.put("rejectedTarget", rejectedTarget);
        values.put("tokens", expectedTokenSequence(shownText));
        if (gateNote != null) {
            values.put("gateNote", gateNote);
        }
        return withCorrection(segment, context, shownText, templates.renderUser(PromptName.PLACEHOLDER_REPAIR, values));
    }

    private List<ChatMessage> withCorrection(
            final Segment segment, final DraftContext context, final String shownText, final String correction) {
        final List<ChatMessage> original = messagesFor(segment, context, shownText);
        return List.of(
                original.getFirst(),
                new ChatMessage(ChatRole.USER, original.get(1).content() + "\n" + correction));
    }

    private String systemMessage() {
        final Map<String, String> slots = frame.systemSlotValues();
        return templates
                .renderSystem(
                        PromptName.DRAFT,
                        Map.of(
                                "source", slots.get("sourceLanguage"),
                                "target", slots.get("targetLanguage"),
                                "styleSheet", slots.get("styleSheet"),
                                "foreignPassageRule", slots.get("foreignPassageRule")))
                .strip();
    }

    private String userMessage(
            final String source, final String target, final String shownText, final DraftContext context) {
        final String summary = context.summary();
        return templates
                .renderUser(
                        PromptName.DRAFT,
                        Map.of(
                                "source",
                                source,
                                "target",
                                target,
                                "tokens",
                                expectedTokenSequence(shownText),
                                "text",
                                shownText,
                                "summary",
                                summary == null || summary.isBlank() ? "" : summary,
                                "glossaryTerms",
                                String.join("\n", context.glossaryLines()),
                                "memoryHint",
                                String.join("\n", context.memoryLines()),
                                "precedingTargets",
                                String.join("\n\n", context.precedingTargets())))
                .strip();
    }

    /**
     * Returns every placeholder in source order within an already-masked text, for the draft prompt, its repairs
     * and the directed fix, whose expected sequence comes from the source rather than the rejected target.
     */
    public static String expectedTokenSequence(final String masked) {
        Objects.requireNonNull(masked, "masked");
        final List<String> tokens = Tokens.inOrder(masked);
        return tokens.isEmpty() ? "(none; do not invent placeholders)" : String.join(" ", tokens);
    }
}
