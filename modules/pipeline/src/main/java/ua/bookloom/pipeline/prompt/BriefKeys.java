package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefChoices;
import ua.bookloom.api.project.QuoteStyle;

/**
 * Reads the Book Brief's choices from one language file (design D6): {@code briefApostrophes} (characters, default
 * first), {@code briefQuotes} (one style per word, four marks each: outer open, outer close, inner open, inner close;
 * default first), the first words of {@code informalAddress} and {@code formalAddress}, and the conventions
 * {@code conventions=} declares with their {@code convention.<key>.choices} and {@code convention.<key>.default}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefKeys {

    private static final int MARKS_PER_STYLE = 4;

    static BriefChoices read(final LanguageFile file) {
        Objects.requireNonNull(file, "file");
        final BriefChoices choices = new BriefChoices(
                words(file, "briefApostrophes").stream()
                        .filter(BriefKeys::isOneCharacter)
                        .toList(),
                quoteStyles(file),
                address(file),
                conventions(file));
        log.debug(
                "Brief choices of {}: apostrophes={} quotes={} address={} conventions={}",
                file.name(),
                choices.apostrophes().size(),
                choices.quoteStyles().size(),
                choices.address() != null,
                choices.conventions().size());
        return choices;
    }

    private static List<QuoteStyle> quoteStyles(final LanguageFile file) {
        final List<QuoteStyle> styles = new ArrayList<>();
        for (final String word : words(file, "briefQuotes")) {
            final int[] marks = word.codePoints().toArray();
            if (marks.length != MARKS_PER_STYLE) {
                log.warn("Quote style '{}' of {} is not four marks and is left out", word, file.name());
                continue;
            }
            styles.add(new QuoteStyle(mark(marks[0]), mark(marks[1]), mark(marks[2]), mark(marks[3])));
        }
        return styles;
    }

    private static BriefChoices.@Nullable AddressWords address(final LanguageFile file) {
        final List<String> informal = words(file, "informalAddress");
        final List<String> formal = words(file, "formalAddress");
        if (informal.isEmpty() || formal.isEmpty()) {
            log.debug("{} lists no address words, so no address row is offered", file.name());
            return null;
        }
        return new BriefChoices.AddressWords(informal.getFirst(), formal.getFirst());
    }

    private static List<BriefChoices.DeclaredConvention> conventions(final LanguageFile file) {
        final List<BriefChoices.DeclaredConvention> declared = new ArrayList<>();
        for (final String key : words(file, "conventions")) {
            final List<String> choices = words(file, "convention." + key + ".choices");
            final List<String> fallback = words(file, "convention." + key + ".default");
            if (choices.size() < 2 || fallback.isEmpty() || !choices.contains(fallback.getFirst())) {
                log.warn("Convention {} of {} names no two choices and a default and is ignored", key, file.name());
                continue;
            }
            declared.add(new BriefChoices.DeclaredConvention(key, choices, fallback.getFirst()));
        }
        return declared;
    }

    private static List<String> words(final LanguageFile file, final String key) {
        final String value = file.get(key);
        return value == null || value.isBlank()
                ? List.of()
                : List.of(value.strip().split("\\s+"));
    }

    private static boolean isOneCharacter(final String text) {
        return text.codePointCount(0, text.length()) == 1;
    }

    private static String mark(final int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
