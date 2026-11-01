package ua.bookloom.pipeline.prompt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The fixed renderings of forms of address ({@code mr}, {@code sir}) in one language pair, from the pair file's
 * {@code titleRenderings} key written {@code source>target}. A model sounds such a word out ({@code ms} as
 * {@code міз}) or answers in the source's letters, so the pair states the right word once; a term written with a
 * capital gets the rendering with a capital, as the book writes it at the start of a sentence.
 */
@Slf4j
public final class TitleRenderings {

    private static final String KEY = "titleRenderings";
    private static final char ARROW = '>';

    private final Map<String, String> byTitle;

    private TitleRenderings(final Map<String, String> byTitle) {
        this.byTitle = byTitle;
    }

    /**
     * The renderings of a language pair.
     *
     * @param sourceTag the source language tag, or null when it is not known
     * @param targetTag the target language tag, or null when it is not known
     * @return the pair's renderings; empty ones when the pair lists none
     */
    public static TitleRenderings forPair(@Nullable final String sourceTag, @Nullable final String targetTag) {
        final Map<String, String> rows = new HashMap<>();
        for (final String row : rowsOf(sourceTag, targetTag)) {
            final int arrow = row.indexOf(ARROW);
            if (arrow > 0 && arrow < row.length() - 1) {
                rows.put(row.substring(0, arrow), row.substring(arrow + 1));
            } else {
                log.warn(
                        "Title rendering '{}' of {}-{} is not source>target; it is ignored", row, sourceTag, targetTag);
            }
        }
        log.debug("Title renderings of {}-{}: {}", sourceTag, targetTag, rows.size());
        return new TitleRenderings(Map.copyOf(rows));
    }

    private static String[] rowsOf(@Nullable final String sourceTag, @Nullable final String targetTag) {
        final String value = LanguageRules.bundled()
                .pairFile(Objects.requireNonNullElse(sourceTag, ""), Objects.requireNonNullElse(targetTag, ""))
                .get(KEY);
        return value == null || value.isBlank() ? new String[0] : value.split("\\s+");
    }

    /**
     * The rendering of a form of address.
     *
     * @param term the non-null term, in any case and with or without a closing full stop
     * @return the target, capitalised when the term is, or empty when the pair lists no such title
     */
    public Optional<String> render(final String term) {
        Objects.requireNonNull(term, "term");
        final String word = term.strip().replaceAll("\\.$", "");
        final String rendering = word.isEmpty() ? null : byTitle.get(word.toLowerCase(Locale.ROOT));
        if (rendering == null) {
            return Optional.empty();
        }
        return Optional.of(Character.isUpperCase(word.codePointAt(0)) ? capitalised(rendering) : rendering);
    }

    private static String capitalised(final String word) {
        final int first = word.codePointAt(0);
        return new StringBuilder()
                .appendCodePoint(Character.toTitleCase(first))
                .append(word.substring(Character.charCount(first)))
                .toString();
    }
}
