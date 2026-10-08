package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.pipeline.Tokens;

/** Renders the catalog's one-segment draft-translation prompt with the currently available context. */
@Slf4j
public final class DraftPromptBuilder {

    /** What the token line says for a text with no token, the case a small model most often invents one for. */
    static final String NO_TOKENS = "(none — write no ⟦gN⟧ token at all; write every name as plain text)";

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
        return messagesFor(segment, context, shownText, "");
    }

    /**
     * As {@link #messagesFor(Segment, DraftContext, String)}, with an instruction the model reads before the text.
     *
     * @param extraInstruction what a repair asks of this draft, one line per finding; empty for a plain draft, which
     *     leaves the message exactly as it is without one
     */
    public List<ChatMessage> messagesFor(
            final Segment segment, final DraftContext context, final String shownText, final String extraInstruction) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shownText, "shownText");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        final String source = PromptLanguages.describe(frame.sourceLanguage());
        final String target = PromptLanguages.describe(frame.targetLanguage());
        log.debug(
                "Building draft prompt sourceLanguage={} targetLanguage={} segmentId={} shownLength={}",
                source,
                target,
                segment.id(),
                shownText.length());
        final String system = systemMessage();
        final String user = userMessage(source, target, shownText, context, extraInstruction);
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
        return messagesForStructuredRepair(segment, context, shownText, "", rejectedReply, diagnostic);
    }

    /** As the {@code shownText} overload, over a draft that carried {@code extraInstruction}. */
    public List<ChatMessage> messagesForStructuredRepair(
            final Segment segment,
            final DraftContext context,
            final String shownText,
            final String extraInstruction,
            final String rejectedReply,
            final String diagnostic) {
        Objects.requireNonNull(rejectedReply, "rejectedReply");
        Objects.requireNonNull(diagnostic, "diagnostic");
        final String correction =
                templates.renderUser(PromptName.STRUCTURAL_REPAIR, structuralValues(rejectedReply, diagnostic));
        return withCorrection(segment, context, shownText, extraInstruction, correction);
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
        return messagesForPlaceholderRepair(segment, context, shownText, "", rejectedTarget, gateNote);
    }

    /** As the {@code shownText} overload, over a draft that carried {@code extraInstruction}. */
    public List<ChatMessage> messagesForPlaceholderRepair(
            final Segment segment,
            final DraftContext context,
            final String shownText,
            final String extraInstruction,
            final String rejectedTarget,
            @Nullable final String gateNote) {
        Objects.requireNonNull(rejectedTarget, "rejectedTarget");
        return withCorrection(
                segment,
                context,
                shownText,
                extraInstruction,
                templates.renderUser(
                        PromptName.PLACEHOLDER_REPAIR, placeholderValues(shownText, rejectedTarget, gateNote)));
    }

    /**
     * The parts of a draft step's prompt as its messages send them, in order: the draft's system and user parts, then
     * the correction a repair adds.
     *
     * @param step the non-null step whose prompt is shown
     * @param context the non-null context the draft is shown
     * @param shownText the non-null text the model translates
     * @param extraInstruction the non-null note under {@code [Extra instruction]}; empty for none
     * @param rejected the rejected reply or target a repair quotes; empty for a draft
     * @param diagnostic what the reader or the gate said of it; empty for a draft or for no gate note
     * @return never null; the filled parts
     */
    public List<PromptSection> sectionsFor(
            final DraftStep step,
            final DraftContext context,
            final String shownText,
            final String extraInstruction,
            final String rejected,
            final String diagnostic) {
        Objects.requireNonNull(step, "step");
        final List<PromptSection> sections = new ArrayList<>(templates.sectionsOf(
                PromptName.DRAFT,
                frame,
                userValues(
                        PromptLanguages.describe(frame.sourceLanguage()),
                        PromptLanguages.describe(frame.targetLanguage()),
                        shownText,
                        context,
                        extraInstruction)));
        switch (step) {
            case DRAFT -> log.debug("A draft adds no correction section");
            case STRUCTURAL_REPAIR ->
                sections.addAll(templates.userSectionsOf(step.promptName(), structuralValues(rejected, diagnostic)));
            case PLACEHOLDER_REPAIR ->
                sections.addAll(templates.userSectionsOf(
                        step.promptName(),
                        placeholderValues(shownText, rejected, diagnostic.isEmpty() ? null : diagnostic)));
        }
        return sections;
    }

    private static Map<String, String> structuralValues(final String rejectedReply, final String diagnostic) {
        return Map.of("rejectedReply", rejectedReply, "diagnostic", diagnostic);
    }

    private static Map<String, String> placeholderValues(
            final String shownText, final String rejectedTarget, @Nullable final String gateNote) {
        final Map<String, String> values = new HashMap<>();
        values.put("rejectedTarget", rejectedTarget);
        values.put("tokens", expectedTokenSequence(shownText));
        if (gateNote != null) {
            values.put("gateNote", gateNote);
        }
        return values;
    }

    private List<ChatMessage> withCorrection(
            final Segment segment,
            final DraftContext context,
            final String shownText,
            final String extraInstruction,
            final String correction) {
        final List<ChatMessage> original = messagesFor(segment, context, shownText, extraInstruction);
        return List.of(
                original.getFirst(),
                new ChatMessage(ChatRole.USER, original.get(1).content() + "\n" + correction));
    }

    private String systemMessage() {
        return templates.renderSystem(PromptName.DRAFT, frame).strip();
    }

    private String userMessage(
            final String source,
            final String target,
            final String shownText,
            final DraftContext context,
            final String extraInstruction) {
        return templates
                .renderUser(PromptName.DRAFT, userValues(source, target, shownText, context, extraInstruction))
                .strip();
    }

    private static Map<String, String> userValues(
            final String source,
            final String target,
            final String shownText,
            final DraftContext context,
            final String extraInstruction) {
        final String summary = context.summary();
        final String following = context.followingTarget();
        return Map.ofEntries(
                Map.entry("source", source),
                Map.entry("target", target),
                Map.entry("tokens", expectedTokenSequence(shownText)),
                Map.entry("text", shownText),
                Map.entry("summary", summary == null || summary.isBlank() ? "" : summary),
                Map.entry("glossaryTerms", linesWhere(context.glossaryLines(), false)),
                Map.entry("lockedNames", linesWhere(context.glossaryLines(), true)),
                Map.entry("suggestedTerms", String.join("\n", context.suggestedLines())),
                Map.entry("memoryHint", String.join("\n", context.memoryLines())),
                Map.entry("lexiconTerms", String.join("\n", context.lexiconLines())),
                Map.entry("characters", String.join("\n", context.characterLines())),
                Map.entry("precedingTargets", String.join("\n\n", context.precedingTargets())),
                Map.entry("followingTarget", following == null ? "" : following),
                Map.entry("extraInstruction", extraInstruction));
    }

    /**
     * The glossary lines that name a locked token ({@code ⟦gN⟧ → name}), or the others: the token explanation is shown
     * only beside a token the segment holds, so a small model is not taught to hide a plain name behind one.
     */
    private static String linesWhere(final List<String> lines, final boolean locked) {
        return String.join(
                "\n",
                lines.stream().filter(line -> line.startsWith("⟦g") == locked).toList());
    }

    /**
     * Returns every placeholder in source order within an already-masked text, for the draft prompt, its repairs
     * and the directed fix, whose expected sequence comes from the source rather than the rejected target.
     */
    public static String expectedTokenSequence(final String masked) {
        Objects.requireNonNull(masked, "masked");
        final List<String> tokens = Tokens.inOrder(masked);
        return tokens.isEmpty() ? NO_TOKENS : String.join(" ", tokens);
    }
}
