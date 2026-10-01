package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.util.lang.LanguageTags;

/**
 * The segments a name scan reads: the book's running text only. A heading, a title page line or a contents entry
 * capitalises every word, so on the fixture book the model scan proposed {@code Gravity Formula}, {@code Six} and
 * {@code Practical Introduction} from chapter titles; a block the book marks as another language is not the source
 * language's names. A real name also occurs in running text, where it is found.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScanText {

    /** The kinds of running text; headings, titles, metadata and alt text are not. */
    private static final Set<SegmentKind> RUNNING_TEXT = Set.of(
            SegmentKind.PARAGRAPH,
            SegmentKind.VERSE_LINE,
            SegmentKind.LIST_ITEM,
            SegmentKind.TABLE_CELL,
            SegmentKind.FOOTNOTE,
            SegmentKind.CAPTION);

    /** A line this short with every word capitalised is a title, not a sentence. */
    private static final int MAX_TITLE_WORDS = 10;

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    /** The small words a title leaves in lower case. */
    private static final Set<String> MINOR_WORDS =
            Set.of("a", "an", "and", "as", "at", "by", "for", "from", "in", "of", "on", "or", "the", "to", "with");

    /**
     * The running text of {@code segments}, as masked texts a scan reads: each running-text segment that does not
     * declare a foreign language, with every inline run that declares one blanked out and every title line dropped.
     *
     * @param segments the segments to read; never null
     * @param sourceLanguage the book's source language tag, or null when unknown — then no text counts as foreign
     * @return the texts, in order, none blank; never null
     */
    static List<String> of(final List<Segment> segments, @Nullable final String sourceLanguage) {
        Objects.requireNonNull(segments, "segments");
        final Optional<String> source = LanguageTags.normalize(sourceLanguage);
        final List<String> texts = segments.stream()
                .filter(segment -> RUNNING_TEXT.contains(segment.kind()))
                .filter(segment -> !isForeign(segment.declaredLanguage(), source))
                .map(segment -> withoutTitleLines(withoutForeignRuns(segment, source)))
                .filter(text -> !text.isBlank())
                .toList();
        log.debug("Scan text: {} running texts of {} segments", texts.size(), segments.size());
        return texts;
    }

    private static boolean isForeign(@Nullable final String declaredLanguage, final Optional<String> source) {
        final Optional<String> declared = LanguageTags.normalize(declaredLanguage);
        return source.isPresent() && declared.isPresent() && !declared.equals(source);
    }

    // A French run inside an English sentence ("La pomme") is not an English name.
    private static String withoutForeignRuns(final Segment segment, final Optional<String> source) {
        String text = segment.masked();
        for (final PlaceholderPair pair : segment.pairs()) {
            final int open = text.indexOf(pair.open());
            final int close = open < 0 ? -1 : text.indexOf(pair.close(), open);
            if (close >= 0 && isForeign(pair.language(), source)) {
                text = text.substring(0, open) + " "
                        + text.substring(close + pair.close().length());
            }
        }
        return text;
    }

    // A plain-text book keeps a contents list or a heading as lines of one paragraph.
    private static String withoutTitleLines(final String text) {
        return LINE_BREAK.splitAsStream(text).filter(line -> !isTitleLine(line)).collect(Collectors.joining("\n"));
    }

    // Every word holding a letter starts with a capital, apart from a title's small words.
    private static boolean isTitleLine(final String line) {
        final List<String> words = Occurrences.read(line).words().stream()
                .map(Occurrences.Word::text)
                .filter(word -> word.codePoints().anyMatch(Character::isLetter))
                .toList();
        final boolean title = words.size() >= 2
                && words.size() <= MAX_TITLE_WORDS
                && words.stream()
                        .allMatch(word -> Character.isUpperCase(word.codePointAt(0))
                                || MINOR_WORDS.contains(Occurrences.keyOf(word)));
        if (title && log.isTraceEnabled()) {
            log.trace("Scan text: title line dropped {}", line);
        }
        return title;
    }
}
