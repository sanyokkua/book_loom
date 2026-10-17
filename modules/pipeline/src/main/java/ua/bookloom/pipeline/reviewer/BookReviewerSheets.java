package ua.bookloom.pipeline.reviewer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.context.InjectedCharacters;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.glossary.GlossaryNames;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * The book's glossary, lexicon and characters as one reviewer call is shown them: only the entries the call's own
 * pairs name, in source or candidate. A reviewer handed the whole chunk's terms was told about names none of its pairs
 * held, and a small model reads every line it is given as a rule to check.
 *
 * @param glossary every glossary entry as the chunk read it
 * @param lexicon the lexicon's entries as the chunk read them
 * @param filter which renderings the lexicon may offer
 * @param analysisLanguage the language the code reads the book in, or null for the generic rules
 * @param characterAllowance the tokens the character sheet may take
 */
@Slf4j
public record BookReviewerSheets(
        List<GlossaryEntry> glossary,
        List<LexiconEntry> lexicon,
        LexiconFilter filter,
        @Nullable String analysisLanguage,
        int characterAllowance)
        implements ReviewerSheets {

    private static final String ALSO = " (also ";
    private static final String ARROW = " → ";

    /** Copies the lists. */
    public BookReviewerSheets {
        glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
        lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
        Objects.requireNonNull(filter, "filter");
    }

    @Override
    public Sheet forPairs(final List<ReviewedPair> pairs) {
        Objects.requireNonNull(pairs, "pairs");
        final List<String> texts = textsOf(pairs);
        final List<String> candidates = pairs.stream()
                .map(pair -> Tokens.replace(pair.maskedCandidate(), " "))
                .toList();
        final List<GlossaryEntry> named = glossary.stream()
                .filter(entry -> isNamed(entry, texts, candidates))
                .toList();
        final List<GlossaryEntry> rendered = named.stream()
                .filter(entry -> !entry.locked())
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .toList();
        final ReviewerCall.Terms terms =
                new ReviewerCall.Terms(linesOf(rendered, false), usualRenderings(texts), linesOf(rendered, true));
        final List<String> characters = InjectedCharacters.within(
                InjectedCharacters.selectIn(withRenderedTerms(texts, named), glossary, analysisLanguage),
                characterAllowance);
        log.debug(
                "Reviewer sheet for pairs={} named={} glossary={} suggested={} usual={} characters={}",
                pairs.size(),
                named.size(),
                terms.glossaryPairs().size(),
                terms.suggestedPairs().size(),
                terms.usualRenderings().size(),
                characters.size());
        return new Sheet(terms, characters);
    }

    // A name is named by its source spelling anywhere, or by its target rendering in a candidate, which the
    // translation may have declined or written alone where the source said "the pilot".
    private static boolean isNamed(final GlossaryEntry entry, final List<String> texts, final List<String> candidates) {
        final String target = entry.target();
        if (texts.stream().anyMatch(text -> GlossaryNames.isNamedIn(entry, text))) {
            return true;
        }
        return target != null && candidates.stream().anyMatch(candidate -> TermMatch.isNamedIn(target, candidate));
    }

    // The character sheet reads names from texts, so a character found by its rendering is added by its term.
    private static List<String> withRenderedTerms(final List<String> texts, final List<GlossaryEntry> named) {
        return Stream.concat(texts.stream(), named.stream().map(GlossaryEntry::term))
                .toList();
    }

    private static List<String> textsOf(final List<ReviewedPair> pairs) {
        return pairs.stream()
                .flatMap(pair -> Stream.of(pair.maskedSource(), pair.maskedCandidate()))
                .map(text -> Tokens.replace(text, " "))
                .toList();
    }

    // An entry is one line however many spellings it has: its term, then "(also …)" with its aliases.
    private static List<String> linesOf(final List<GlossaryEntry> entries, final boolean isSuggested) {
        return entries.stream()
                .filter(entry -> entry.isSuggested() == isSuggested)
                .map(BookReviewerSheets::lineOf)
                .toList();
    }

    private static String lineOf(final GlossaryEntry entry) {
        final List<String> aliases = entry.aliases();
        final String name = aliases.isEmpty() ? entry.term() : entry.term() + ALSO + String.join(", ", aliases) + ")";
        return name + ARROW + entry.target();
    }

    private List<String> usualRenderings(final List<String> texts) {
        return lexicon.stream()
                .filter(entry -> !heldByGlossary(entry))
                .filter(entry -> texts.stream().anyMatch(text -> TermMatch.isNamedIn(entry.term(), text)))
                .flatMap(entry -> entry
                        .established()
                        .filter(rendering -> filter.admits(entry.term(), rendering))
                        .map(rendering -> entry.term() + ARROW + rendering)
                        .stream())
                .toList();
    }

    private boolean heldByGlossary(final LexiconEntry entry) {
        final String key = LexiconEntry.keyOf(entry.term());
        return glossary.stream()
                .anyMatch(held -> LexiconEntry.keyOf(held.term()).equals(key));
    }
}
