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
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.Tokens;

/** Renders the catalog's one-segment draft-translation prompt with the currently available context. */
@Slf4j
public final class DraftPromptBuilder {

    private final PromptTemplates templates;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;
    private final StyleSheet styleSheet;
    private final ForeignPassagePolicy foreignPassages;

    /** Creates a builder for one job's resolved languages, style sheet and foreign-passage policy. */
    public DraftPromptBuilder(
            final PromptTemplates templates,
            @Nullable final String sourceLanguage,
            final String targetLanguage,
            final StyleSheet styleSheet,
            final ForeignPassagePolicy foreignPassages) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        this.styleSheet = Objects.requireNonNull(styleSheet, "styleSheet");
        this.foreignPassages = Objects.requireNonNull(foreignPassages, "foreignPassages");
    }

    /** The job's source language tag, or null when it is inferred from the text. */
    public @Nullable String sourceLanguage() {
        return sourceLanguage;
    }

    /** The job's target language tag. */
    public String targetLanguage() {
        return targetLanguage;
    }

    /** Builds the catalog system and user messages for one masked segment. */
    public List<ChatMessage> messagesFor(final Segment segment) {
        return messagesFor(segment, DraftContext.empty());
    }

    /** Builds the catalog system and user messages for one masked segment with its available prior targets. */
    public List<ChatMessage> messagesFor(final Segment segment, final DraftContext context) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        final String source = resolvedSourceLanguage();
        final String target = PromptLanguages.describe(targetLanguage);
        log.debug(
                "Building draft prompt sourceLanguage={} targetLanguage={} segmentId={} maskedLength={}",
                source,
                target,
                segment.id(),
                segment.masked().length());
        final String system = systemMessage(source, target);
        final String user = userMessage(source, target, segment, context);
        if (log.isTraceEnabled()) {
            log.trace("Draft prompt system={} user={}", system, user);
        }
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    /** Builds a correction request after a reply does not match the required response object. */
    public List<ChatMessage> messagesForStructuredRepair(
            final Segment segment, final DraftContext context, final String rejectedReply, final String diagnostic) {
        Objects.requireNonNull(rejectedReply, "rejectedReply");
        Objects.requireNonNull(diagnostic, "diagnostic");
        final String correction = templates.renderUser(
                PromptName.STRUCTURAL_REPAIR, Map.of("rejectedReply", rejectedReply, "diagnostic", diagnostic));
        return withCorrection(segment, context, correction);
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
        Objects.requireNonNull(rejectedTarget, "rejectedTarget");
        final Map<String, String> values = new HashMap<>();
        values.put("rejectedTarget", rejectedTarget);
        values.put("tokens", expectedTokenSequence(segment));
        if (gateNote != null) {
            values.put("gateNote", gateNote);
        }
        return withCorrection(segment, context, templates.renderUser(PromptName.PLACEHOLDER_REPAIR, values));
    }

    private List<ChatMessage> withCorrection(
            final Segment segment, final DraftContext context, final String correction) {
        final List<ChatMessage> original = messagesFor(segment, context);
        return List.of(
                original.getFirst(),
                new ChatMessage(ChatRole.USER, original.get(1).content() + "\n" + correction));
    }

    private String resolvedSourceLanguage() {
        return PromptLanguages.describe(sourceLanguage);
    }

    private String systemMessage(final String source, final String target) {
        return templates
                .renderSystem(
                        PromptName.DRAFT,
                        Map.of(
                                "source",
                                source,
                                "target",
                                target,
                                "styleSheet",
                                styleSheet.text(),
                                "foreignPassageRule",
                                StyleSheet.foreignPassageRule(foreignPassages, source)))
                .strip();
    }

    private String userMessage(
            final String source, final String target, final Segment segment, final DraftContext context) {
        return templates
                .renderUser(
                        PromptName.DRAFT,
                        Map.of(
                                "source", source,
                                "target", target,
                                "tokens", expectedTokenSequence(segment),
                                "text", segment.masked(),
                                "precedingTargets", String.join("\n\n", context.precedingTargets())))
                .strip();
    }

    /** Returns every placeholder in source order for the user-facing integrity reminder and repair request. */
    public static String expectedTokenSequence(final Segment segment) {
        return expectedTokenSequence(segment.masked());
    }

    /**
     * Returns every placeholder in source order within an already-masked text, for a repair call built from a
     * masked source rather than a {@link Segment} (the directed fix, whose expected sequence comes from the source,
     * not the rejected target).
     */
    public static String expectedTokenSequence(final String masked) {
        Objects.requireNonNull(masked, "masked");
        final List<String> tokens = Tokens.inOrder(masked);
        return tokens.isEmpty() ? "(none; do not invent placeholders)" : String.join(" ", tokens);
    }
}
