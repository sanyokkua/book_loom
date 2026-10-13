package ua.bookloom.pipeline.glossary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Gives each suggestion the facts {@link LockRule} weighs, where the suggestions of one run are complete (15i.T4). A
 * brand is code-decided here: its rendering is its source whatever the model answered, or did not, so the draft never
 * sees its Latin letters and the Latin-run gate has nothing to block.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionLocks {

    /**
     * The suggestions with their lock facts, a brand's rendering replaced by its source.
     *
     * @param entries the entries the run was asked about; never null
     * @param suggestions the run's suggestions, at most one per entry; never null
     * @param frame the run's language pair; never null
     * @param policy the Book Brief's name policy; never null
     * @param context the readings and the verified names; never null
     * @return the suggestions in their order, a brand's added where the model gave none; never null
     */
    static List<Suggestion> apply(
            final List<GlossaryEntry> entries,
            final List<Suggestion> suggestions,
            final CallFrame frame,
            final NamePolicy policy,
            final TargetContext context) {
        final boolean inflects = LanguageRules.bundled().namesInflect(frame.targetLanguage());
        final Map<String, Suggestion> byId = new LinkedHashMap<>();
        suggestions.forEach(suggestion -> byId.putIfAbsent(suggestion.entry().id(), suggestion));
        for (final GlossaryEntry entry : entries) {
            if (isBrand(entry, context)) {
                log.debug("Entry {} is a brand: its rendering is its source", entry.id());
                byId.put(entry.id(), new Suggestion(entry, entry.term()));
            }
        }
        log.debug("Lock facts for {} suggestions, names inflect {}", byId.size(), inflects);
        return byId.values().stream()
                .map(suggestion -> suggestion.locking(basisOf(suggestion.entry(), policy, context), inflects))
                .toList();
    }

    private static boolean isBrand(final GlossaryEntry entry, final TargetContext context) {
        final Reading reading = context.readings().get(entry.id());
        return reading != null && reading.brand();
    }

    // A kept suggestion has passed the identity rule and, under Transliterate, the letter table.
    private static LockRule.Basis basisOf(
            final GlossaryEntry entry, final NamePolicy policy, final TargetContext context) {
        final Reading reading = context.readings().get(entry.id());
        return new LockRule.Basis(
                isBrand(entry, context),
                policy == NamePolicy.KEEP_ORIGINAL && entry.type() != TermType.TERM,
                context.verified().contains(entry.id()),
                reading != null && reading.speaking(),
                policy == NamePolicy.TRANSLITERATE);
    }
}
