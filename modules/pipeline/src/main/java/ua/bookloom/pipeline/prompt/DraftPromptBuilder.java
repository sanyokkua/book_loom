package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;

/** Renders the catalog's one-segment draft-translation prompt with the currently available context. */
@Slf4j
public final class DraftPromptBuilder {

    private static final String UNKNOWN_SOURCE_LANGUAGE = "the language of this segment (infer it from its text)";
    private static final Pattern PLACEHOLDER = Pattern.compile("⟦g\\d+⟧");

    private final PromptTemplates templates;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;

    /** Creates a builder for one job's resolved languages. */
    public DraftPromptBuilder(
            final PromptTemplates templates, @Nullable final String sourceLanguage, final String targetLanguage) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
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
        final String target = languageDescription(targetLanguage);
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

    /** Builds a correction request after a structurally valid target fails the document placeholder hard gate. */
    public List<ChatMessage> messagesForPlaceholderRepair(
            final Segment segment, final DraftContext context, final String rejectedTarget) {
        Objects.requireNonNull(rejectedTarget, "rejectedTarget");
        final String correction = templates.renderUser(
                PromptName.PLACEHOLDER_REPAIR,
                Map.of("rejectedTarget", rejectedTarget, "tokens", expectedTokenSequence(segment)));
        return withCorrection(segment, context, correction);
    }

    private List<ChatMessage> withCorrection(
            final Segment segment, final DraftContext context, final String correction) {
        final List<ChatMessage> original = messagesFor(segment, context);
        return List.of(
                original.getFirst(),
                new ChatMessage(ChatRole.USER, original.get(1).content() + "\n" + correction));
    }

    private String resolvedSourceLanguage() {
        return sourceLanguage == null ? UNKNOWN_SOURCE_LANGUAGE : languageDescription(sourceLanguage);
    }

    private String systemMessage(final String source, final String target) {
        return templates
                .renderSystem(
                        PromptName.DRAFT,
                        Map.of("source", source, "target", target, "styleSheet", DefaultStyleSheet.TEXT))
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
        final Matcher matcher = PLACEHOLDER.matcher(segment.masked());
        final List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens.isEmpty() ? "(none; do not invent placeholders)" : String.join(" ", tokens);
    }

    private static String languageDescription(final String languageTag) {
        final String displayName = Locale.forLanguageTag(languageTag).getDisplayName(Locale.ENGLISH);
        if (displayName.isBlank() || displayName.equalsIgnoreCase(languageTag)) {
            return "language tag \"" + languageTag + "\"";
        }
        return displayName + " (" + languageTag + ")";
    }
}
