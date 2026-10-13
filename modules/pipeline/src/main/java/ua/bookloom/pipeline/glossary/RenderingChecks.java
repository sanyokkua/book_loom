package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.IdentityRule.Kept;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Standing;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The rules a suggested rendering must meet beyond its shape (15i.N6): the one identity rule, and under Transliterate
 * the target's letter table for a name that is neither a term nor a speaking name. Which entries are speaking names or
 * brands comes from their readings; an entry never read is neither.
 */
@Slf4j
final class RenderingChecks {

    private final CallFrame frame;
    private final NamePolicy policy;
    private final Map<String, Reading> readings;
    private final Map<String, List<String>> table;

    RenderingChecks(final CallFrame frame, final NamePolicy policy, final Map<String, Reading> readings) {
        this.frame = frame;
        this.policy = policy;
        this.readings = readings;
        this.table = LanguageRules.bundled().translit(frame.targetLanguage());
    }

    /**
     * Where a rendering stands against the rules.
     *
     * @param entry the entry the rendering is for
     * @param rendering the stripped, one-line rendering
     * @return {@link Standing#COPY} or {@link Standing#LETTERS} for a refused one, {@link Standing#VERBATIM} for a
     *     brand or an identifier kept as written, {@link Standing#OPEN} otherwise
     */
    Standing standing(final GlossaryEntry entry, final String rendering) {
        final Reading reading = readings.get(entry.id());
        final boolean isBrand = reading != null && reading.brand();
        final Kept kept = isBrand ? Kept.BRAND : Kept.NOT_KEPT;
        final String term = entry.term();
        if (term.strip().equalsIgnoreCase(rendering.strip())) {
            return copyStanding(term, rendering, kept);
        }
        final boolean isSpeaking = reading != null && reading.speaking();
        final boolean isJudged = policy == NamePolicy.TRANSLITERATE && entry.type() != TermType.TERM && !isSpeaking;
        if (isJudged && !TransliterationCheck.holds(term, rendering, table)) {
            log.debug("Rendering of entry {} does not open as the name transliterates", entry.id());
            log.trace("Rendering '{}' of '{}' fails the letter table", rendering, term);
            return Standing.LETTERS;
        }
        return Standing.OPEN;
    }

    private Standing copyStanding(final String term, final String rendering, final Kept kept) {
        if (IdentityRule.refuses(term, rendering, frame.analysisLanguage(), frame.targetLanguage(), kept)) {
            return Standing.COPY;
        }
        return kept != Kept.NOT_KEPT || IdentityRule.isIdentifier(term) ? Standing.VERBATIM : Standing.OPEN;
    }
}
