package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the draft prompt carries besides the segment: the book so far, the names in play, earlier decisions and the
 * neighbouring translated text. An empty part is left out of the prompt entirely.
 *
 * @param precedingTargets the earlier segments' target text of the same unit, in order, as plain text
 * @param summary the rolling summary, or null when none exists yet
 * @param glossaryLines one line per injected glossary term whose target, if any, the person chose
 * @param memoryLines one {@code source → target} line per translation-memory hint or suggestion
 * @param suggestedLines one line per injected glossary term whose target the model suggested and nobody confirmed,
 *     shown apart as a hint rather than a rendering to apply exactly
 * @param lexiconLines one {@code term → rendering} line per recurring term whose rendering the book has established,
 *     shown as a "keep consistent" hint; it never overrides the glossary
 * @param characterLines one {@code name — gender} line per character of the segment whose gender the glossary knows
 * @param followingTarget the next segment's target as plain text, or null when it has none; a run drafts ahead of the
 *     next segment and never has one, a retry shows the one the book holds by then
 * @param wholeParagraph the paragraph a sentence group belongs to as plain display text, or null when the draft is not a
 *     group (the 15i.X3 arm); it carries no placeholder token, so no group can copy another group's token
 */
public record DraftContext(
        List<String> precedingTargets,
        @Nullable String summary,
        List<String> glossaryLines,
        List<String> memoryLines,
        List<String> suggestedLines,
        List<String> lexiconLines,
        List<String> characterLines,
        @Nullable String followingTarget,
        @Nullable String wholeParagraph) {

    /**
     * Rejects null entries and makes the context immutable at the prompt boundary, leaving out empty and
     * placeholder lines and repeats so no prompt carries filler (see {@link PromptHygiene}).
     */
    public DraftContext {
        precedingTargets = PromptHygiene.clean(Objects.requireNonNull(precedingTargets, "precedingTargets"));
        summary = PromptHygiene.cleanText(summary);
        glossaryLines = PromptHygiene.clean(Objects.requireNonNull(glossaryLines, "glossaryLines"));
        memoryLines = PromptHygiene.clean(Objects.requireNonNull(memoryLines, "memoryLines"));
        suggestedLines = PromptHygiene.clean(Objects.requireNonNull(suggestedLines, "suggestedLines"));
        lexiconLines = PromptHygiene.clean(Objects.requireNonNull(lexiconLines, "lexiconLines"));
        characterLines = PromptHygiene.clean(Objects.requireNonNull(characterLines, "characterLines"));
        followingTarget = PromptHygiene.cleanText(followingTarget);
        wholeParagraph = PromptHygiene.cleanText(wholeParagraph);
    }

    /** A context that is not a sentence group of a longer paragraph, as every draft but the 15i.X3 arm's. */
    public DraftContext(
            final List<String> precedingTargets,
            @Nullable final String summary,
            final List<String> glossaryLines,
            final List<String> memoryLines,
            final List<String> suggestedLines,
            final List<String> lexiconLines,
            final List<String> characterLines,
            @Nullable final String followingTarget) {
        this(
                precedingTargets,
                summary,
                glossaryLines,
                memoryLines,
                suggestedLines,
                lexiconLines,
                characterLines,
                followingTarget,
                null);
    }

    /** A context with no following target, as every draft of a run has. */
    public DraftContext(
            final List<String> precedingTargets,
            @Nullable final String summary,
            final List<String> glossaryLines,
            final List<String> memoryLines,
            final List<String> suggestedLines,
            final List<String> lexiconLines,
            final List<String> characterLines) {
        this(precedingTargets, summary, glossaryLines, memoryLines, suggestedLines, lexiconLines, characterLines, null);
    }

    /**
     * The same context showing the next segment's target after the preceding ones.
     *
     * @param target the next segment's target as plain text, or null for none
     * @return the context with that following target
     */
    public DraftContext withFollowingTarget(@Nullable final String target) {
        return new DraftContext(
                precedingTargets,
                summary,
                glossaryLines,
                memoryLines,
                suggestedLines,
                lexiconLines,
                characterLines,
                target,
                wholeParagraph);
    }

    /**
     * The same context for one sentence group of a longer paragraph, which it shows whole.
     *
     * @param paragraph the whole paragraph as plain display text with no placeholder token, or null for none
     * @return the context with that paragraph
     */
    public DraftContext withWholeParagraph(@Nullable final String paragraph) {
        return new DraftContext(
                precedingTargets,
                summary,
                glossaryLines,
                memoryLines,
                suggestedLines,
                lexiconLines,
                characterLines,
                followingTarget,
                paragraph);
    }

    /** A context with no character sheet. */
    public DraftContext(
            final List<String> precedingTargets,
            @Nullable final String summary,
            final List<String> glossaryLines,
            final List<String> memoryLines,
            final List<String> suggestedLines,
            final List<String> lexiconLines) {
        this(precedingTargets, summary, glossaryLines, memoryLines, suggestedLines, lexiconLines, List.of());
    }

    /** A context with no recurring-term renderings. */
    public DraftContext(
            final List<String> precedingTargets,
            @Nullable final String summary,
            final List<String> glossaryLines,
            final List<String> memoryLines,
            final List<String> suggestedLines) {
        this(precedingTargets, summary, glossaryLines, memoryLines, suggestedLines, List.of());
    }

    /** A context with no suggested rendering. */
    public DraftContext(
            final List<String> precedingTargets,
            @Nullable final String summary,
            final List<String> glossaryLines,
            final List<String> memoryLines) {
        this(precedingTargets, summary, glossaryLines, memoryLines, List.of(), List.of());
    }

    /** A context holding only preceding targets. */
    public DraftContext(final List<String> precedingTargets) {
        this(precedingTargets, null, List.of(), List.of());
    }

    /** Returns the empty context used at the start of a section. */
    public static DraftContext empty() {
        return new DraftContext(List.of());
    }
}
