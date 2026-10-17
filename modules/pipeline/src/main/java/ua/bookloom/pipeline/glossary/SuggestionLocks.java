package ua.bookloom.pipeline.glossary;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Gives each suggestion the facts {@link LockRule} weighs, where the suggestions of one run are complete (15i.T4,
 * 15j.J1). A brand follows the name policy exactly as a person's name does: written in the target script, translated or
 * kept and locked as the policy says. A term that is not a name (a material, a substance, a drug, a unit: any common
 * noun) is translated and never locked as a name.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionLocks {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * The suggestions with their lock facts.
     *
     * @param suggestions the run's suggestions, at most one per entry; never null
     * @param frame the run's language pair; never null
     * @param policy the Book Brief's name policy; never null
     * @param context the readings and the verified names; never null
     * @return the suggestions in their order; never null
     */
    static List<Suggestion> apply(
            final List<Suggestion> suggestions,
            final CallFrame frame,
            final NamePolicy policy,
            final TargetContext context) {
        final boolean inflects = LanguageRules.bundled().namesInflect(frame.targetLanguage());
        final Map<String, Suggestion> byId = new LinkedHashMap<>();
        suggestions.forEach(suggestion -> byId.putIfAbsent(suggestion.entry().id(), suggestion));
        log.debug("Lock facts for {} suggestions, names inflect {}", byId.size(), inflects);
        return byId.values().stream()
                .map(suggestion -> suggestion.locking(basisOf(suggestion.entry(), policy, context), inflects))
                .toList();
    }

    /**
     * Whether the entry is a name, or a brand, which a name policy treats as a name. A character or a place is a
     * name; a title or another entry is one only when the readings verified it as a proper name or it is a phrase of
     * capitalised words ({@code Harbour Guild}); a term is one only when called a brand; a demoted common noun is
     * translated, whatever the policy.
     */
    static boolean isNameLike(final GlossaryEntry entry, final TargetContext context) {
        final Reading reading = context.readings().get(entry.id());
        if (reading != null && reading.brand()) {
            return true;
        }
        return switch (entry.type()) {
            case CHARACTER, PLACE -> true;
            case TITLE, OTHER -> context.verified().contains(entry.id()) || isCapitalisedPhrase(entry.term());
            case TERM -> false;
        };
    }

    // A single capitalised word may be a demoted common noun (Rose); a run of capitalised words is a proper name.
    private static boolean isCapitalisedPhrase(final String term) {
        final String[] words = SPACES.split(term.strip());
        return words.length > 1
                && Arrays.stream(words)
                        .allMatch(word -> word.equals("&") || Character.isUpperCase(word.codePointAt(0)));
    }

    /** Under Keep original a name or a brand keeps its source spelling, so only a common noun needs the model. */
    static boolean isKeptAsWritten(final GlossaryEntry entry, final NamePolicy policy, final TargetContext context) {
        return policy == NamePolicy.KEEP_ORIGINAL && isNameLike(entry, context);
    }

    // A kept suggestion has passed the identity rule and, under Transliterate, the letter table.
    private static LockRule.Basis basisOf(
            final GlossaryEntry entry, final NamePolicy policy, final TargetContext context) {
        final Reading reading = context.readings().get(entry.id());
        final boolean kept = isKeptAsWritten(entry, policy, context);
        log.debug("Entry {} (type {}) is {}", entry.id(), entry.type(), kept ? "kept as written" : "left to the model");
        return new LockRule.Basis(
                kept,
                context.verified().contains(entry.id()) && isNameLike(entry, context),
                reading != null && reading.speaking(),
                policy == NamePolicy.TRANSLITERATE);
    }
}
