package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpan;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * Picks the glossary entries a segment's prompt lists. A small model's context is precious, so only the entries whose
 * term occurs in the text being translated are sent; a locked entry the model never sees is described by its token.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class InjectedTerms {

    static List<InjectedTerm> select(
            final List<Segment> segments, final ProtectedMask mask, final List<GlossaryEntry> glossary) {
        final List<String> searchTexts = Tokens.visibleTexts(segments);
        final List<InjectedTerm> selected = new ArrayList<>();
        for (final GlossaryEntry entry : glossary) {
            if (!occursIn(entry.term(), searchTexts)) {
                continue;
            }
            final SnapshotTerm term = snapshotOf(entry);
            final List<String> lines = lines(term, mask);
            if (lines.isEmpty()) {
                log.debug("Glossary term {} is locked and hidden only in other segments; left out", entry.term());
            } else {
                selected.add(new InjectedTerm(term, lines));
                log.trace("Glossary term {} occurs in the text and is offered", entry.term());
            }
        }
        log.trace("Glossary scoped to the text offered={} occurring={}", glossary.size(), selected.size());
        return selected;
    }

    static boolean hasTarget(final SnapshotTerm term) {
        final String target = term.target();
        return target != null && !target.isBlank();
    }

    private static boolean occursIn(final String term, final List<String> searchTexts) {
        return !term.isBlank() && searchTexts.stream().anyMatch(text -> TermMatch.isNamedIn(term, text));
    }

    /**
     * The prompt lines of one term: its rendering and facts, or — for a locked term — one line per token it hides
     * behind in {@code mask}, none when it is hidden only in other segments.
     */
    static List<String> lines(final SnapshotTerm entry, final ProtectedMask mask) {
        final String target = entry.target();
        final String facts = entry.type().name().toLowerCase(Locale.ROOT) + ", "
                + entry.gender().name().toLowerCase(Locale.ROOT);
        if (target == null || target.isBlank()) {
            return List.of(entry.term() + " (" + facts + ")");
        }
        if (!entry.locked()) {
            return List.of(entry.term() + " → " + target + " (" + facts + ")");
        }
        return hiddenTokens(entry.term(), target, mask).stream()
                .map(token -> token + " → " + target + ", " + facts)
                .toList();
    }

    private static List<String> hiddenTokens(final String term, final String target, final ProtectedMask mask) {
        if (!mask.presentLocked().contains(new LockedRendering(term, target))) {
            return List.of();
        }
        return mask.spans().stream()
                .filter(span ->
                        span.check() == CheckName.LOCKED_TERM && span.restored().equals(target))
                .map(ProtectedSpan::token)
                .toList();
    }

    private static SnapshotTerm snapshotOf(final GlossaryEntry entry) {
        return new SnapshotTerm(
                entry.term(), entry.target(), entry.type(), entry.gender(), entry.locked(), entry.isSuggested());
    }
}
