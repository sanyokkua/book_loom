package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.IdentityRule.Kept;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Standing;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TransliterationCheck.Problem;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The rules a suggested rendering must meet beyond its shape (15i.N6, 15j.J1, 15j.J4, 15j.J10): the one identity rule;
 * a letter the target language does not use, for every rendering; and for a name or a brand that is not a speaking
 * name every capitalised word of the name kept, under Transliterate the target's letter table, and the dictionary form
 * of a name that ends in a consonant. Which entries are speaking names or brands comes from their readings; an entry
 * never read is neither, and a term that is not a brand is a common noun, judged by none of the name rules.
 */
@Slf4j
final class RenderingChecks {

    private static final Pattern LAST_WORD = Pattern.compile("[^\\s-]+$");

    private final CallFrame frame;
    private final NamePolicy policy;
    private final Map<String, Reading> readings;
    private final Map<String, List<String>> table;
    private final String forbiddenLetters;
    private final List<String> caseEndings;
    private final String openFinals;
    private final Set<String> articles;

    RenderingChecks(final CallFrame frame, final NamePolicy policy, final Map<String, Reading> readings) {
        this.frame = frame;
        this.policy = policy;
        this.readings = readings;
        final LanguageRules rules = LanguageRules.bundled();
        this.table = rules.translit(frame.targetLanguage());
        this.forbiddenLetters = rules.forbiddenLetters(frame.targetLanguage());
        this.caseEndings = rules.wordsOf(frame.targetLanguage(), "nonNominativeEndings");
        final String analysis = frame.analysisLanguage();
        this.openFinals = analysis == null ? "" : String.join("", rules.wordsOf(analysis, "openNameFinals"));
        this.articles = analysis == null ? Set.of() : Set.copyOf(rules.wordsOf(analysis, "articles"));
    }

    /**
     * Where a rendering stands against the rules.
     *
     * @param entry the entry the rendering is for
     * @param rendering the stripped, one-line rendering
     * @return the refusing {@link Standing} for a refused one, {@link Standing#VERBATIM} for an identifier kept as
     *     written, {@link Standing#OPEN} otherwise
     */
    Standing standing(final GlossaryEntry entry, final String rendering) {
        final String term = entry.term();
        if (term.strip().equalsIgnoreCase(rendering.strip())) {
            return copyStanding(term, rendering);
        }
        if (IdentityRule.isIdentifier(term)) {
            log.debug("Rendering of entry {} changes an identifier", entry.id());
            return Standing.IDENTIFIER;
        }
        if (hasForbiddenLetter(rendering)) {
            log.debug("Rendering of entry {} holds a letter the target language does not use", entry.id());
            log.trace("Rendering '{}' of '{}' holds a letter outside the alphabet", rendering, term);
            return Standing.ALPHABET;
        }
        final Reading reading = readings.get(entry.id());
        final boolean isBrand = reading != null && reading.brand();
        final boolean isSpeaking = reading != null && reading.speaking();
        final boolean isName = entry.type() != TermType.TERM || isBrand;
        if (policy == NamePolicy.KEEP_ORIGINAL || !isName || isSpeaking) {
            return Standing.OPEN;
        }
        return nameStanding(entry, rendering);
    }

    private Standing nameStanding(final GlossaryEntry entry, final String rendering) {
        final String term = entry.term();
        final Optional<Problem> problem =
                TransliterationCheck.problem(term, rendering, table, policy == NamePolicy.TRANSLITERATE, articles);
        if (problem.isPresent()) {
            log.debug("Rendering of entry {}: {}", entry.id(), problem.get());
            log.trace("Rendering '{}' of '{}': {}", rendering, term, problem.get());
            return problem.get() == Problem.LOST_WORD ? Standing.LOST_WORD : Standing.LETTERS;
        }
        if (policy == NamePolicy.TRANSLITERATE && isCaseForm(term, rendering)) {
            log.debug("Rendering of entry {} is a case form of a name that ends in a consonant", entry.id());
            log.trace("Rendering '{}' of '{}' ends in a case ending", rendering, term);
            return Standing.OBLIQUE;
        }
        return Standing.OPEN;
    }

    private boolean hasForbiddenLetter(final String rendering) {
        return rendering.toLowerCase(Locale.ROOT).codePoints().anyMatch(point -> forbiddenLetters.indexOf(point) >= 0);
    }

    // A name that ends in a consonant sound stays so in the dictionary form: the rendering is a case form only when it
    // is the source's own transliteration plus an oblique ending (Valdor, not Валдором) - the letter before the ending
    // stands for the source's last letter (language file translit) and the stem is no shorter than the source. A name
    // that merely ends like one (Том, Львів) or is no transliteration of the source is kept; a lower-case last word is
    // a translated common word (річка); a language that lists no open final letters judges nothing.
    private boolean isCaseForm(final String term, final String rendering) {
        final String name = lastWord(term).toLowerCase(Locale.ROOT);
        final String word = lastWord(rendering);
        if (openFinals.isEmpty() || name.isEmpty() || word.isEmpty() || !Character.isUpperCase(word.codePointAt(0))) {
            return false;
        }
        final String finalLetter = name.substring(name.length() - 1);
        final List<String> letters = table.getOrDefault(finalLetter, List.of());
        final String lower = word.toLowerCase(Locale.ROOT);
        return Character.isLetter(finalLetter.charAt(0))
                && !openFinals.contains(finalLetter)
                && caseEndings.stream().anyMatch(ending -> isStemPlus(lower, ending, name.length(), letters));
    }

    private static boolean isStemPlus(
            final String word, final String ending, final int sourceLength, final List<String> letters) {
        final int stemLength = word.length() - ending.length();
        return word.endsWith(ending)
                && stemLength >= sourceLength
                && letters.contains(word.substring(stemLength - 1, stemLength));
    }

    private static String lastWord(final String name) {
        final Matcher last = LAST_WORD.matcher(name.strip());
        return last.find() ? last.group() : "";
    }

    private Standing copyStanding(final String term, final String rendering) {
        if (IdentityRule.refuses(term, rendering, frame.analysisLanguage(), frame.targetLanguage(), Kept.NOT_KEPT)) {
            return Standing.COPY;
        }
        return IdentityRule.isIdentifier(term) ? Standing.VERBATIM : Standing.OPEN;
    }
}
