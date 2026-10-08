package ua.bookloom.pipeline.glossary;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.text.JunkRules;

/**
 * What a glossary name may not be, whoever proposes it (the frequency scan or the model): an English contraction or
 * possessive (<code>Flint'll</code>, <code>Corvin's</code>), half of a longer name that a connector joins
 * (<code>Smith</code> of <code>Smith &amp; Wesson</code>), a common word of the language, or junk. The rules are
 * deliberately narrow — a first name that is also the start of a full name stays, since it is an alias the book uses
 * — and each rejection is logged with its reason. An empty target is not judged here: the suggestion step fills it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameHygiene {

    private static final Pattern CONTRACTION =
            Pattern.compile("\\p{L}['’ʼ](?:ll|s|d|re|ve|m|t)(?![\\p{L}\\p{M}])", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORDS = Pattern.compile("\\s+");
    private static final Set<String> SYMBOL_CONNECTORS = Set.of("&", "+");

    /**
     * Why a name candidate should be left out of the glossary.
     *
     * @param term the non-blank candidate
     * @param candidates every term proposed or held beside it, itself included
     * @param languageTag the source language's tag, or null for English
     * @return the reason, or empty when the candidate is fine
     */
    public static Optional<String> rejection(
            final String term, final Collection<String> candidates, @Nullable final String languageTag) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(candidates, "candidates");
        final Optional<String> reason = contraction(term, languageTag)
                .or(() -> fragment(term, candidates, languageTag))
                .or(() -> commonWord(term, languageTag));
        reason.ifPresent(why -> {
            log.debug("Name candidate dropped by hygiene: {}", why);
            log.trace("Name candidate {} dropped by hygiene: {}", term, why);
        });
        return reason;
    }

    private static boolean isEnglish(@Nullable final String languageTag) {
        return languageTag == null || languageTag.toLowerCase(Locale.ROOT).startsWith("en");
    }

    private static Optional<String> contraction(final String term, @Nullable final String languageTag) {
        return isEnglish(languageTag) && CONTRACTION.matcher(term).find()
                ? Optional.of("an English contraction or possessive, not a name")
                : Optional.empty();
    }

    private static Optional<String> fragment(
            final String term, final Collection<String> candidates, @Nullable final String languageTag) {
        final List<String> words = wordsOf(term);
        final Set<String> stop = StopWords.of(languageTag);
        return candidates.stream()
                .map(NameHygiene::wordsOf)
                .filter(longer -> longer.size() > words.size())
                .filter(longer -> isEdgeOf(words, longer))
                .filter(longer -> hasConnector(words, longer, stop))
                .findFirst()
                .map(longer -> "a fragment of the longer name \"" + String.join(" ", longer) + "\"");
    }

    private static boolean isEdgeOf(final List<String> words, final List<String> longer) {
        return outside(words, longer) != null;
    }

    // The words of the longer name outside the fragment, or null when the fragment is not at either edge.
    private static @Nullable List<String> outside(final List<String> words, final List<String> longer) {
        final List<String> wanted = lower(words);
        if (lower(longer.subList(0, words.size())).equals(wanted)) {
            return longer.subList(words.size(), longer.size());
        }
        final int cut = longer.size() - words.size();
        return lower(longer.subList(cut, longer.size())).equals(wanted) ? longer.subList(0, cut) : null;
    }

    // A connector there (and, of, &) makes the fragment half of a pair or a title, where a plain extra name word would
    // make it an alias.
    private static boolean hasConnector(final List<String> words, final List<String> longer, final Set<String> stop) {
        final List<String> rest = Objects.requireNonNull(outside(words, longer), "rest");
        return lower(rest).stream().anyMatch(word -> SYMBOL_CONNECTORS.contains(word) || stop.contains(word));
    }

    private static Optional<String> commonWord(final String term, @Nullable final String languageTag) {
        final String key = term.strip().toLowerCase(Locale.ROOT);
        if (wordsOf(key).size() == 1 && StopWords.of(languageTag).contains(key)) {
            return Optional.of("a common word of the language");
        }
        return JunkRules.isJunk(term) ? Optional.of("junk text, not a name") : Optional.empty();
    }

    private static List<String> wordsOf(final String term) {
        return WORDS.splitAsStream(term.strip()).toList();
    }

    private static List<String> lower(final List<String> words) {
        return words.stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
    }
}
