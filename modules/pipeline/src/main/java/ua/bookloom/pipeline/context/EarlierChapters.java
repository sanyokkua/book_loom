package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.pipeline.project.ChapterBoundaries;

/**
 * Chooses and writes the earlier chapters' summaries of block 8 (design D10, "Use"): every kept summary of a chapter
 * before the focus's chapter, ordered by the chapter's place in the book rather than by when it was saved, so a summary
 * written again on Continue never moves. A summary whose key no chapter of the book has now is passed over.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EarlierChapters {

    // A summary is one line of the block, so a line break inside one would read as the next chapter's line.
    private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\R\\s*");

    /**
     * The summaries a call whose first item is {@code segmentId} carries.
     *
     * @param stored the non-null kept summaries, in any order
     * @param frame the non-null run frame, whose chapters number them; the caller has read the switch, which the frame's
     *     brief holds only as it stood at Start
     * @param segmentId the non-null id of the call's first item
     * @return never null; oldest first, empty when the item is in the first chapter or in none
     */
    static List<EarlierSummary> forFocus(
            final List<ChapterSummary> stored, final RunFrame frame, final String segmentId) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(segmentId, "segmentId");
        final ChapterBoundaries chapters = frame.chapters();
        final Optional<ChapterBoundaries.Chapter> focus = chapters.chapterOf(segmentId);
        if (focus.isEmpty()) {
            log.debug("Earlier chapters none segmentId={}: it is in no chapter", segmentId);
            return List.of();
        }
        final List<EarlierSummary> eligible = new ArrayList<>();
        for (final ChapterSummary summary : stored) {
            numberOf(chapters, summary.chapterKey())
                    .filter(number -> number < focus.get().number())
                    .ifPresent(
                            number -> eligible.add(new EarlierSummary(summary.chapterKey(), number, summary.text())));
        }
        eligible.sort(Comparator.comparingInt(EarlierSummary::chapter));
        log.debug(
                "Earlier chapters focusChapter={} stored={} eligible={} keys={}",
                focus.get().number(),
                stored.size(),
                eligible.size(),
                eligible.stream().map(EarlierSummary::chapterKey).toList());
        return List.copyOf(eligible);
    }

    // The budgets drop the oldest summaries first, so the kept ones are the newest: the last of those offered.
    static List<String> keptKeys(final List<EarlierSummary> offered, final int kept) {
        final List<String> keys = offered.subList(offered.size() - kept, offered.size()).stream()
                .map(EarlierSummary::chapterKey)
                .toList();
        if (!offered.isEmpty()) {
            log.debug("Earlier chapters offered={} kept={} keys={}", offered.size(), kept, keys);
        }
        return keys;
    }

    /**
     * One summary's line.
     *
     * @param summary the non-null summary
     * @param words the non-null block wording
     * @return the line; never null
     */
    static String line(final EarlierSummary summary, final BlockWords words) {
        final String text = LINE_BREAKS.matcher(summary.text().strip()).replaceAll(" ");
        return words.get(BlockWords.EARLIER_LINE)
                .replace("{chapter}", Integer.toString(summary.chapter()))
                .replace("{text}", text);
    }

    /**
     * The earlier-chapters part of block 8: the heading and one line per summary, with no blank line between them.
     *
     * @param lines the non-null lines, oldest first
     * @param words the non-null block wording
     * @return the part; empty when there is no line
     */
    static String block(final List<String> lines, final BlockWords words) {
        if (lines.isEmpty()) {
            return "";
        }
        final String block = words.get(BlockWords.EARLIER_HEADING) + "\n" + String.join("\n", lines);
        if (log.isTraceEnabled()) {
            log.trace("Earlier chapters block {}", block);
        }
        return block;
    }

    /**
     * Whether a block 8 text opens with the earlier chapters' heading.
     *
     * @param text the non-null text of block 8
     * @return {@code true} if it carries summaries, {@code false} if only the card
     */
    static boolean opensBlock(final String text) {
        return text.startsWith(BlockWords.bundled().get(BlockWords.EARLIER_HEADING));
    }

    private static Optional<Integer> numberOf(final ChapterBoundaries chapters, final String chapterKey) {
        final Optional<Integer> number = chapters.chapters().stream()
                .filter(chapter -> chapter.key().equals(chapterKey))
                .map(ChapterBoundaries.Chapter::number)
                .findFirst();
        if (number.isEmpty()) {
            log.debug("Earlier chapters pass over key={}: no chapter of the book has it now", chapterKey);
        }
        return number;
    }
}
