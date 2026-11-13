package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code article-gravity} reference book (redesign task 1.11): one invented science article in English, Ukrainian,
 * Russian and French, each as Markdown, EPUB and FB2, and the spans a translation must keep byte for byte. The spans
 * are read from the English Markdown, so the structural test and the article eval measure the same list.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ArticleGravity {

    static final String ID = "article-gravity";
    static final String SOURCE = "en";
    static final List<String> LANGUAGES = List.of("en", "uk", "ru", "fr");
    static final List<String> TARGETS = List.of("uk", "ru", "fr");
    static final String STEM = "gravity";
    static final String IMAGE = "Picture_of_earth.jpg";

    private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]>\"]+");
    private static final Pattern DOI = Pattern.compile("\\b10\\.\\d{4,9}/[^\\s)\\]\"]+");
    private static final Pattern FENCE = Pattern.compile("(?ms)^```(\\w*)\\n(.*?)^```$");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z][A-Za-z0-9]*_[A-Za-z0-9_]+\\b");

    /** The dataset folder on the test classpath. */
    static Path folder() {
        try {
            return Path.of(Objects.requireNonNull(ArticleGravity.class.getResource("/eval/datasets/" + ID), ID)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("dataset folder unreadable: " + ID, e);
        }
    }

    /**
     * One file of the book.
     *
     * @param language one of {@link #LANGUAGES}
     * @param extension {@code md}, {@code epub} or {@code fb2}
     */
    static Path file(final String language, final String extension) {
        return folder().resolve(language).resolve(STEM + "." + extension);
    }

    /** The Markdown text of one language. */
    static String markdown(final String language) {
        try {
            return Files.readString(file(language, "md"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every distinct {@code http(s)} URL of a Markdown text, in reading order. */
    static List<String> urls(final String markdown) {
        return matches(URL, markdown);
    }

    /** Every distinct DOI ({@code 10.NNNN/...}) of a Markdown text, in reading order. */
    static List<String> dois(final String markdown) {
        return matches(DOI, markdown);
    }

    /** The info string (language) of every fenced code block, in order and with repeats. */
    static List<String> fenceLanguages(final String markdown) {
        return FENCE.matcher(markdown).results().map(m -> m.group(1)).toList();
    }

    /** Every distinct snake_case identifier inside the fenced code blocks, in reading order. */
    static List<String> codeIdentifiers(final String markdown) {
        final String code =
                FENCE.matcher(markdown).results().map(m -> m.group(2)).reduce("", String::concat);
        return matches(IDENTIFIER, code);
    }

    /** The spans a translation keeps verbatim: the URLs, the DOIs and the code identifiers, distinct, in that order. */
    static List<String> protectedSpans(final String markdown) {
        final Set<String> spans = new LinkedHashSet<>(urls(markdown));
        spans.addAll(dois(markdown));
        spans.addAll(codeIdentifiers(markdown));
        return List.copyOf(spans);
    }

    /**
     * The share of the source's protected spans the translation holds verbatim.
     *
     * @param source the source Markdown
     * @param translation the translated Markdown
     * @return a value in {@code [0, 1]}; 1 when the source has no span
     */
    static double keptShare(final String source, final String translation) {
        final List<String> spans = protectedSpans(source);
        final List<String> lost = lostSpans(source, translation);
        log.debug("Article protected spans total={} lost={}", spans.size(), lost.size());
        return spans.isEmpty() ? 1.0 : (spans.size() - lost.size()) / (double) spans.size();
    }

    /** The source's protected spans the translation does not hold verbatim, in the source's order; empty when none. */
    static List<String> lostSpans(final String source, final String translation) {
        final List<String> lost = protectedSpans(source).stream()
                .filter(span -> !translation.contains(span))
                .toList();
        log.trace("Article protected spans lost {}", lost);
        return lost;
    }

    private static List<String> matches(final Pattern pattern, final String text) {
        return List.copyOf(pattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .collect(LinkedHashSet<String>::new, Set::add, Set::addAll));
    }
}
