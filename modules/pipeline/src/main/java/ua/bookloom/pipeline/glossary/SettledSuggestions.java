package ua.bookloom.pipeline.glossary;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.TitleRenderings;

/**
 * The suggestions that need no model call: a name or a brand kept as written under Keep original, and a form of
 * address that the language pair's data renders. The model is never asked about these, so it cannot spoil them.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SettledSuggestions {

    // The types a form of address can carry: the glossary's title, and a recurring word or an other entry.
    private static final Set<TermType> RULED_TYPES = EnumSet.of(TermType.TITLE, TermType.TERM, TermType.OTHER);

    static List<Suggestion> of(
            final List<GlossaryEntry> entries,
            final CallFrame frame,
            final NamePolicy policy,
            final TargetContext context) {
        final List<Suggestion> kept = entries.stream()
                .filter(entry -> SuggestionLocks.isKeptAsWritten(entry, policy, context))
                .map(entry -> new Suggestion(entry, entry.term()))
                .toList();
        final TitleRenderings titles = TitleRenderings.forPair(frame.analysisLanguage(), frame.targetLanguage());
        final List<Suggestion> ruled = entries.stream()
                .filter(entry -> RULED_TYPES.contains(entry.type()))
                .filter(entry -> !SuggestionLocks.isKeptAsWritten(entry, policy, context))
                .flatMap(entry ->
                        titles.render(entry.term()).map(rendering -> Suggestion.byRule(entry, rendering)).stream())
                .toList();
        log.debug(
                "Settled without a call: {} kept as written, {} rendered by the pair's data",
                kept.size(),
                ruled.size());
        return Stream.concat(kept.stream(), ruled.stream()).toList();
    }
}
