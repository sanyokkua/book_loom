package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * Cuts the text around one mention: the sentences of the segments next to the mention's own inside its unit, taken
 * outwards one at a time (the one before first) while the whole stays inside the token budget. The mention's own
 * sentence is always kept.
 */
@Slf4j
@RequiredArgsConstructor
final class WindowCutter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final IndexData data;

    /** One sentence and the segment it came from. */
    private record Piece(int ordinal, String text) {}

    /** The sentences around a mention, and where the mention lies in its own sentence. */
    private record Region(List<Piece> pieces, int mentionAt, int markOffset, int markLength) {}

    Window cut(final Mention mention, final WindowPlan plan) {
        final Region region = region(mention, Math.max(0, plan.neighbours()));
        int low = region.mentionAt();
        int high = region.mentionAt();
        boolean beforeNext = true;
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

    private Region region(final Mention mention, final int neighbours) {
        final UnitSpan span = data.spans().get(mention.ordinal());
        final int from = Math.max(span.first(), mention.ordinal() - neighbours);
        final int to = Math.min(span.last(), mention.ordinal() + neighbours);
        final List<Piece> pieces = new ArrayList<>();
        int mentionAt = 0;
        int markOffset = 0;
        int markLength = 0;
        for (int ordinal = from; ordinal <= to; ordinal++) {
            final Occurrences.Read read = Occurrences.read(data.texts().get(ordinal));
            if (ordinal == mention.ordinal()) {
                mentionAt = pieces.size() + mention.sentence();
                final int sentenceStart = sentenceStart(read, mention.sentence());
                markOffset = collapse(read.text().substring(sentenceStart, mention.start()))
                        .length();
                markLength = collapse(read.text().substring(mention.start(), mention.end()))
                        .length();
            }
            for (final String sentence : read.sentences()) {
                pieces.add(new Piece(ordinal, sentence));
            }
        }
        return new Region(pieces, mentionAt, markOffset, markLength);
    }

    private static int sentenceStart(final Occurrences.Read read, final int sentence) {
        return read.words().stream()
                .filter(word -> word.sentence() == sentence)
                .mapToInt(Occurrences.Word::start)
                .findFirst()
                .orElse(0);
    }

    private static String collapse(final String text) {
        return WHITESPACE.matcher(text).replaceAll(" ");
    }

    private static String join(final List<Piece> pieces, final int from, final int to) {
        final StringBuilder text = new StringBuilder();
        for (int index = from; index <= to; index++) {
            text.append(index > from ? " " : "").append(pieces.get(index).text());
        }
        return text.toString();
    }
}
