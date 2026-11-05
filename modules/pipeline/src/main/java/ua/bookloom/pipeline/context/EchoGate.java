package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Tells a translation that copies a shown example (design D9): a small model sometimes pastes an example's sentence
 * instead of translating its item. An item copies when its target shares a run of {@value #ECHO_NGRAM} words with an
 * example's target that its own source does not hold, so a source that truly says the same words is never refused. A
 * mark in an example stands for whatever word the model put in its place, since a copied example keeps its words and
 * changes the name.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EchoGate {

    /** How many words in a row make a copy: three are common phrases, four are a sentence's fingerprint. */
    public static final int ECHO_NGRAM = 4;

    // A gram of the example must hold this many real words; a mark alone proves nothing.
    private static final int REAL_WORDS_MIN = ECHO_NGRAM - 1;
    private static final Pattern WORD = Pattern.compile("⟦[^⟧]*⟧|\\p{L}[\\p{L}\\p{N}’'-]*|\\p{N}+");
    private static final String MARK = "⟦";
    // A pair file's examples hold each reply as a JSON item, whose target is the example's target side.
    private static final Pattern EXAMPLE_TARGET = Pattern.compile("\"target\"\\s*:\\s*\"");

    /**
     * Whether a translation copies one of the run's examples.
     *
     * @param target the non-null masked translation of the item
     * @param source the non-null masked source of the item
     * @param examples the non-null examples the run's calls show
     * @return {@code true} if the target shares a word {@value #ECHO_NGRAM}-gram with an example's target that the
     *     source lacks, {@code false} otherwise or when the run shows no example
     */
    public static boolean copies(final String target, final String source, final ExampleChoice examples) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(examples, "examples");
        if (examples.kind() == ExampleChoice.Kind.NONE) {
            return false;
        }
        final List<String[]> sourceGrams = grams(source);
        final List<String[]> targetGrams = grams(target).stream()
                .filter(gram -> realWords(gram) >= REAL_WORDS_MIN)
                .toList();
        final List<String[]> copied = new ArrayList<>();
        for (final String[] shown : exampleGrams(examples)) {
            if (anyMatch(shown, targetGrams) && !anyMatch(shown, sourceGrams)) {
                copied.add(shown);
            }
        }
        log.debug("Echo gate examples={} copiedGrams={}", examples.kind(), copied.size());
        if (log.isTraceEnabled() && !copied.isEmpty()) {
            log.trace("Echo gate copied gram={}", String.join(" ", copied.getFirst()));
        }
        return !copied.isEmpty();
    }

    private static List<String[]> exampleGrams(final ExampleChoice examples) {
        final List<String> targets = targetValues(examples.text());
        return (targets.isEmpty() ? List.of(examples.text()) : targets)
                .stream()
                        .flatMap(text -> grams(text).stream())
                        .filter(gram -> realWords(gram) >= REAL_WORDS_MIN)
                        .toList();
    }

    // The raw text of each quoted "target" value; scanned by hand because a regex for a quoted string with escapes
    // recurses once per character and overflows the stack on a long value.
    private static List<String> targetValues(final String text) {
        final List<String> values = new ArrayList<>();
        final Matcher matcher = EXAMPLE_TARGET.matcher(text);
        int from = 0;
        while (matcher.find(from)) {
            final int start = matcher.end();
            int end = start;
            while (end < text.length() && text.charAt(end) != '"') {
                end += text.charAt(end) == '\\' ? 2 : 1;
            }
            if (end >= text.length()) {
                break;
            }
            values.add(text.substring(start, end));
            from = end + 1;
        }
        return values;
    }

    // A mark becomes a null that matches any word.
    private static List<String[]> grams(final String text) {
        final List<@Nullable String> words = WORD.matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .map(found -> found.group().startsWith(MARK) ? null : found.group())
                .toList();
        final List<String[]> grams = new ArrayList<>();
        for (int start = 0; start + ECHO_NGRAM <= words.size(); start++) {
            grams.add(words.subList(start, start + ECHO_NGRAM).toArray(String[]::new));
        }
        return grams;
    }

    private static boolean anyMatch(final String[] shown, final List<String[]> written) {
        return written.stream().anyMatch(gram -> matches(shown, gram));
    }

    private static boolean matches(final String[] shown, final String[] written) {
        for (int index = 0; index < ECHO_NGRAM; index++) {
            if (shown[index] != null && written[index] != null && !shown[index].equals(written[index])) {
                return false;
            }
        }
        return true;
    }

    private static long realWords(final String[] gram) {
        return Arrays.stream(gram).filter(Objects::nonNull).count();
    }
}
