package ua.bookloom.pipeline.index;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.util.lang.LanguageTags;
import ua.bookloom.util.text.JunkRules;

/**
 * The running text a name scan reads: the book's paragraphs only. A heading, a title page line or a contents entry
 * capitalises every word, so on the fixture book the model scan proposed {@code Gravity Formula}, {@code Six} and
 * {@code Practical Introduction} from chapter titles; a block the book marks as another language is not the source
 * language's names. A real name also occurs in running text, where it is found.
 *
 * <p>What counts as foreign depends on the source language the caller names, so the index keeps one view per language
 * and builds it once, on the first scan that asks.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScanReads {

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
     * Reads the running text of every segment of the book once.
     *
     * @param data what the build left of the book
     * @param sourceLanguage the book's source language tag, or null when unknown, when no text counts as foreign
     * @return each running-text segment's scan read by ordinal; a segment that is not running text, declares a foreign
     *     language or is left blank is absent
     */
    static Map<Integer, Occurrences.Read> of(final IndexData data, @Nullable final String sourceLanguage) {
        final Optional<String> source = LanguageTags.normalize(sourceLanguage);
        final Map<Integer, Occurrences.Read> reads = new HashMap<>();
        for (int ordinal = 0; ordinal < data.infos().size(); ordinal++) {
            if (!RUNNING_TEXT.contains(data.infos().get(ordinal).kind())) {
                continue;
            }
            final SegmentFacts facts = data.facts().get(ordinal);
            if (isForeign(facts.declaredLanguage(), source)) {
                continue;
            }
            final String masked = data.texts().get(ordinal);
            final String text = withoutTitleLines(withoutForeignRuns(masked, facts, source));
            if (!text.isBlank()) {
                // Nothing was cut: the read made at the build is the very same one.
                reads.put(ordinal, text.equals(masked) ? data.reads().get(ordinal) : Occurrences.read(text));
            }
        }
        log.debug(
                "Scan view of language {}: {} running texts of {} segments",
                source,
                reads.size(),
                data.infos().size());
        return reads;
    }

    /**
     * Whether a mention lies in the story's running text: a paragraph-like segment of a body unit that is not the
     * auxiliary one, outside a title line or a printing note. Speech counts; a heading, contents entry, front or back
     * matter does not.
     */
    static boolean isStoryText(final IndexData data, final Mention mention) {
        final SegmentInfo info = data.infos().get(mention.ordinal());
        if (info.auxiliary() || info.role() != UnitRole.BODY || !RUNNING_TEXT.contains(info.kind())) {
            return false;
        }
        final String line = lineAt(data.reads().get(mention.ordinal()).text(), mention.start());
        return !JunkRules.isPrintingNote(line) && !isTitleLine(line);
    }

    private static String lineAt(final String text, final int position) {
        final int from = Math.max(text.lastIndexOf('\n', position), text.lastIndexOf('\r', position)) + 1;
        final int newline = text.indexOf('\n', position);
        final int carriage = text.indexOf('\r', position);
        final int to = Math.min(newline < 0 ? text.length() : newline, carriage < 0 ? text.length() : carriage);
        return text.substring(from, to);
    }

    private static boolean isForeign(@Nullable final String declaredLanguage, final Optional<String> source) {
        final Optional<String> declared = LanguageTags.normalize(declaredLanguage);
        return source.isPresent() && declared.isPresent() && !declared.equals(source);
    }

    // A French run inside an English sentence ("La pomme") is not an English name.
    private static String withoutForeignRuns(
            final String masked, final SegmentFacts facts, final Optional<String> source) {
        String text = masked;
        for (final PlaceholderPair pair : facts.pairs()) {
            final int open = text.indexOf(pair.open());
            final int close = open < 0 ? -1 : text.indexOf(pair.close(), open);
            if (close >= 0 && isForeign(pair.language(), source)) {
                text = text.substring(0, open) + " "
                        + text.substring(close + pair.close().length());
            }
        }
        return text;
    }

    // A plain-text book keeps a contents list or a heading as lines of one paragraph; a printing note is a line too.
    private static String withoutTitleLines(final String text) {
        return LINE_BREAK
                .splitAsStream(text)
                .filter(line -> !isPrintingNote(line))
                .filter(line -> !isTitleLine(line))
                .collect(Collectors.joining("\n"));
    }

    private static boolean isPrintingNote(final String line) {
        final boolean note = JunkRules.isPrintingNote(line);
        if (note) {
            log.debug("Scan text: printing note dropped");
            log.trace("Scan text: printing note dropped {}", line);
        }
        return note;
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
