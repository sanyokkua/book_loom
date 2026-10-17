package ua.bookloom.pipeline.lexicon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Trusts a vowel alternation (Тім, Том) only when no other glossary rendering accounts for the word: the words the
 * target spends on another rendering, written as it is, are set aside before the alternation may match this one.
 * Built once per target, so the patterns exist only for the renderings the target actually holds.
 */
@Slf4j
public final class AlternationGuard {

    private final String target;
    private final Map<String, Pattern> held;

    private AlternationGuard(final String target, final Map<String, Pattern> held) {
        this.target = target;
        this.held = held;
    }

    /**
     * Finds which of the glossary's renderings the target already holds as written.
     *
     * @param target the non-null target text
     * @param renderings the non-null renderings of every glossary entry
     * @return the guard for that target; never null
     */
    public static AlternationGuard over(final String target, final Collection<String> renderings) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(renderings, "renderings");
        final String lowered = normalised(target);
        final Map<String, Pattern> held = new LinkedHashMap<>();
        for (final String rendering : renderings) {
            final String key = normalised(rendering.strip());
            if (!key.isEmpty() && !held.containsKey(key) && lowered.contains(key)) {
                final Pattern word = Pattern.compile("(?<![\\p{L}\\p{M}])" + Pattern.quote(key) + "[\\p{L}\\p{M}]*");
                if (word.matcher(lowered).find()) {
                    held.put(key, word);
                }
            }
        }
        log.trace("Alternation guard: {} rendering(s) held as written", held.size());
        return new AlternationGuard(lowered, held);
    }

    /**
     * Whether the target proves the rendering, with the alternation trusted only for words no other rendering holds.
     *
     * @param term the non-null source term
     * @param rendering the non-null rendering the glossary gives it
     * @param source the non-null source text that must hold the term
     * @param alternations the non-null alternation data of the target language
     * @return {@code true} if the target holds the rendering, {@code false} otherwise
     */
    public boolean holds(
            final String term, final String rendering, final String source, final List<String> alternations) {
        final Map<String, String> claim = Map.of(term, rendering);
        if (TermMappingVerifier.verify(claim, List.of(term), source, target, alternations)
                .isEmpty()) {
            return false;
        }
        final String own = normalised(rendering.strip());
        String rest = target;
        for (final Map.Entry<String, Pattern> other : held.entrySet()) {
            if (!own.contains(other.getKey()) && !other.getKey().contains(own)) {
                rest = other.getValue().matcher(rest).replaceAll(" ");
            }
        }
        final boolean kept = rest.equals(target)
                || !TermMappingVerifier.verify(claim, List.of(term), source, rest, alternations)
                        .isEmpty();
        if (!kept) {
            log.debug("Alternation guard: the match was another rendering's word");
        }
        return kept;
    }

    private static String normalised(final String text) {
        return text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('ʼ', '\'');
    }
}
