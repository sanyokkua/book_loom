package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * Cuts the text around one mention: the sentences of the segments next to the mention's own inside its unit, taken
 * outwards one at a time (the one before first, or the one after when the plan grows forward first) while the whole
 * stays inside the token budget. The mention's own
 * sentence is always kept; one longer than the budget (and than {@link #MIN_SENTENCE_TOKENS}) is cut at word
 * boundaries around the mention. A sentence keeps its quotation marks and a speaker's dash, sentences of one segment
 * are joined by a space and segments by a line break, so speech stays recognisable in the window's text.
 */
@Slf4j
@RequiredArgsConstructor
final class WindowCutter {

    /** A window keeps at least this many tokens of its mention's own sentence, however small the budget. */
    static final int MIN_SENTENCE_TOKENS = 50;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final IndexData data;

    /** One sentence and the segment it came from. */
    private record Piece(int ordinal, String text) {}

    /** The sentences around a mention, and where the mention lies in its own sentence. */
    private record Region(List<Piece> pieces, int mentionAt, int markOffset, int markLength) {}

    Window cut(final Mention mention, final WindowPlan plan) {
        final Region region = fitted(region(mention, Math.max(0, plan.neighbours())), mention, plan);
        int low = region.mentionAt();
        int high = region.mentionAt();
        boolean beforeNext = plan.growth() == WindowPlan.Growth.BACKWARD_FIRST;
        while (low > 0 || high < region.pieces().size() - 1) {
            final boolean takeBefore =
                    low > 0 && (beforeNext || high == region.pieces().size() - 1);
            final int newLow = takeBefore ? low - 1 : low;
            final int newHigh = takeBefore ? high : high + 1;
            if (TokenEstimator.estimate(join(region.pieces(), newLow, newHigh), data.language())
                    > plan.tokensPerWindow()) {
                break;
            }
            low = newLow;
            high = newHigh;
            beforeNext = !takeBefore;
        }
        return window(mention, region, low, high);
    }

    private Window window(final Mention mention, final Region region, final int low, final int high) {
        final List<Piece> pieces = region.pieces();
        final int before = low < region.mentionAt()
                ? join(pieces, low, region.mentionAt() - 1).length() + 1
                : 0;
        final int markStart = before + region.markOffset();
        log.debug(
                "Window around '{}' at segment {}: sentences {}..{} of {}",
                mention.name(),
                mention.ordinal(),
                low,
                high,
                pieces.size());
        return new Window(
                mention,
                pieces.get(low).ordinal(),
                pieces.get(high).ordinal(),
                join(pieces, low, high),
                markStart,
                markStart + region.markLength());
    }

    // A sentence over the budget would otherwise fill the window alone, however long it is.
    private Region fitted(final Region region, final Mention mention, final WindowPlan plan) {
        final Piece own = region.pieces().get(region.mentionAt());
        final int limit = Math.max(plan.tokensPerWindow(), MIN_SENTENCE_TOKENS);
        if (TokenEstimator.estimate(own.text(), data.language()) <= limit) {
            return region;
        }
        final SentenceCut cut =
                SentenceCut.around(own.text(), region.markOffset(), region.markLength(), limit, data.language(), plan);
        log.debug(
                "Sentence of '{}' at segment {} is over {} tokens: cut from {} to {} characters",
                mention.name(),
                mention.ordinal(),
                limit,
                own.text().length(),
                cut.text().length());
        final List<Piece> pieces = new ArrayList<>(region.pieces());
        pieces.set(region.mentionAt(), new Piece(own.ordinal(), cut.text()));
        return new Region(pieces, region.mentionAt(), cut.markOffset(), region.markLength());
    }

    private Region region(final Mention mention, final int neighbours) {
        final UnitSpan span = data.spans().get(mention.ordinal());
        final int from = Math.max(span.first(), mention.ordinal() - neighbours);
        final int to = Math.min(span.last(), mention.ordinal() + neighbours);
        final List<Piece> pieces = new ArrayList<>();
        int mentionAt = 0;
        int markOffset = 0;
        int markLength = 0;
        for (int ordinal = from; ordinal <= to; ordinal++) {
            final Occurrences.Read read = data.reads().get(ordinal);
            final int[] cuts = cuts(read);
            if (ordinal == mention.ordinal()) {
                mentionAt = pieces.size() + mention.sentence();
                markOffset = collapse(read.text().substring(cuts[mention.sentence()], mention.start()))
                        .stripLeading()
                        .length();
                markLength = collapse(read.text().substring(mention.start(), mention.end()))
                        .length();
            }
            for (int sentence = 0; sentence + 1 < cuts.length; sentence++) {
                pieces.add(new Piece(
                        ordinal,
                        collapse(read.text().substring(cuts[sentence], cuts[sentence + 1]))
                                .strip()));
            }
        }
        return new Region(pieces, mentionAt, markOffset, markLength);
    }

    /**
     * Where each sentence of a segment starts in its text, and its end: a sentence runs from the last blank before its
     * first word, so an opening quotation mark or a speaker's dash stays with the words it opens.
     */
    private static int[] cuts(final Occurrences.Read read) {
        final List<Occurrences.Word> words = read.words();
        final int sentences = read.sentences().size();
        final int[] cuts = new int[sentences + 1];
        cuts[sentences] = read.text().length();
        for (int index = 1; index < words.size(); index++) {
            final Occurrences.Word word = words.get(index);
            if (word.sentence() != words.get(index - 1).sentence()) {
                final int gapStart = words.get(index - 1).end();
                final String gap = read.text().substring(gapStart, word.start());
                final int blank = lastBlank(gap);
                cuts[word.sentence()] = blank < 0 ? word.start() : gapStart + blank + 1;
            }
        }
        return cuts;
    }

    private static int lastBlank(final String gap) {
        for (int index = gap.length() - 1; index >= 0; index--) {
            if (Character.isWhitespace(gap.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static String collapse(final String text) {
        return WHITESPACE.matcher(text).replaceAll(" ");
    }

    private static String join(final List<Piece> pieces, final int from, final int to) {
        final StringBuilder text = new StringBuilder();
        for (int index = from; index <= to; index++) {
            if (index > from) {
                text.append(pieces.get(index).ordinal() == pieces.get(index - 1).ordinal() ? " " : "\n");
            }
            text.append(pieces.get(index).text());
        }
        return text.toString();
    }
}
