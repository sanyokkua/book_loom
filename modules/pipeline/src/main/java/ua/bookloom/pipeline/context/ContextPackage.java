package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * One draft's context as the old assembler builds it: what the prompt carries and the items it selected for it.
 *
 * @param draftContext the prompt's optional blocks
 * @param injected the items the assembler selected, as texts, which its log line counts
 */
public record ContextPackage(DraftContext draftContext, Injected injected) {

    /** Rejects a missing component. */
    public ContextPackage {
        Objects.requireNonNull(draftContext, "draftContext");
        Objects.requireNonNull(injected, "injected");
    }

    /**
     * The items one draft's context was given, before they became prompt lines.
     *
     * @param precedingTargets the preceding segments' target texts, in order
     * @param glossary the glossary terms the segment names
     * @param tmHits the translation-memory hits
     * @param summary the rolling summary text, or null when none existed yet
     * @param styleSheet the style sheet text
     * @param lexicon the established recurring-term renderings the segment names
     * @param characters the character gender sheet, one line per character present
     */
    public record Injected(
            List<String> precedingTargets,
            List<SnapshotTerm> glossary,
            List<SnapshotTmHit> tmHits,
            @Nullable String summary,
            String styleSheet,
            List<SnapshotRendering> lexicon,
            List<String> characters) {

        /** Rejects a missing list and copies them. */
        public Injected {
            precedingTargets = List.copyOf(Objects.requireNonNull(precedingTargets, "precedingTargets"));
            glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
            tmHits = List.copyOf(Objects.requireNonNull(tmHits, "tmHits"));
            Objects.requireNonNull(styleSheet, "styleSheet");
            lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
            characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
        }
    }
}
