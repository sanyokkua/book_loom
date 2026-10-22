package ua.bookloom.pipeline.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.Sentences;
import ua.bookloom.pipeline.checks.StoryMeasure;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.project.SegmentLocators;

/**
 * Takes single windows of a book's story for the brief suggestion, so the brief is judged from the whole book and not
 * from its opening, and each window is read on its own. The windows are spread from the start of the first story
 * chapter to the last story chapter, never overlap, and are sized in tokens so each fits the model's window.
 *
 * <p>Story is what a chapter of the book's body holds by {@link StoryMeasure#isStory}: front and back matter, the
 * metadata unit, a contents list, a converter's page, a page of praise and a heading-only unit are not read. A book with no such chapter is read whole, minus its
 * short lines.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BookSamples {

    private static final int FAST_WINDOWS = 3;
    private static final int BALANCED_WINDOWS = 4;
    private static final int MAX_WINDOWS = 5;
    private static final int FAST_WINDOW_TOKENS = 1_500;
    private static final int BALANCED_WINDOW_TOKENS = 2_500;
    private static final int MAX_WINDOW_TOKENS = 4_000;
    // Below this a window shows too little of the book to judge, whatever a tiny model window leaves.
    private static final int MIN_WINDOW_TOKENS = 200;
    private static final int MIN_FALLBACK_CHARS = 40;
    private static final int PERCENT = 100;
    private static final String LOCATOR_SEPARATOR = " · ";

    private record Line(int paragraph, String text, String segmentId) {}

    private record Para(String segmentId, String text) {}

    private record Window(int from, int to) {}

    /**
     * How many single windows the brief reads.
     *
     * @param dial the quality dial; never null
     * @return 3 for Fast, 4 for Balanced, 5 for Max
     */
    static int windowCount(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> FAST_WINDOWS;
            case BALANCED -> BALANCED_WINDOWS;
            case MAX -> MAX_WINDOWS;
        };
    }

    /**
     * The most tokens one window may hold: the dial's own size (1.5k, 2.5k, 4k), limited to the room the model's window
     * leaves after the call's fixed part and the safety margin every setup call keeps.
     *
     * @param dial the quality dial; never null
     * @param detectedContext the context length the provider reported, or null; limited as
     *     {@code ContextBudget.windowFor(detectedContext, override)} limits it
     * @param override the window the person set for the model, or null; replaces the detected one
     * @param fixedTokens the tokens the call's system message, its other user text and the reply's cap take; not
     *     negative
     * @return the window's budget in tokens; at least 200, so a window is never empty
     */
    static int windowTokens(
            final QualityDial dial,
            @Nullable final Integer detectedContext,
            @Nullable final Integer override,
            final int fixedTokens) {
        final int window = ContextBudget.windowFor(detectedContext, override);
        final int room = window - ContextBudget.SAFETY_MARGIN - fixedTokens;
        final int wanted =
                switch (Objects.requireNonNull(dial, "dial")) {
                    case FAST -> FAST_WINDOW_TOKENS;
                    case BALANCED -> BALANCED_WINDOW_TOKENS;
                    case MAX -> MAX_WINDOW_TOKENS;
                };
        final int tokens = Math.max(MIN_WINDOW_TOKENS, Math.min(wanted, room));
        log.debug(
                "Brief window budget: dial={} model window={} override={} fixed={} room={} -> {} tokens",
                dial,
                window,
                override,
                fixedTokens,
                room,
                tokens);
        return tokens;
    }

    /** As the four-argument form for a model whose window the person did not set. */
    static int windowTokens(final QualityDial dial, @Nullable final Integer detectedContext, final int fixedTokens) {
        return windowTokens(dial, detectedContext, null, fixedTokens);
    }

    /**
     * Single windows of the story, spread from the start of the first story chapter to the last story chapter.
     *
     * @param document the opened book; only its story chapters are read
     * @param languageTag the source language for the token estimate and the dialogue share; null for the general table
     * @param windows how many windows are wanted at most
     * @param windowTokens the most tokens (as {@code TokenEstimator} measures them) one window may hold
     * @return up to {@code windows} samples of one passage each, in reading order, never overlapping; fewer when the
     *     story is too short for that many, one at least when the book has story text; empty when it has none
     */
    static List<BookSample> of(
            final Document document, @Nullable final String languageTag, final int windows, final int windowTokens) {
        Objects.requireNonNull(document, "document");
        final List<Line> lines = lines(storyChapters(chapters(document)));
        final Map<String, SegmentLocator> locators = SegmentLocators.of(document);
        if (lines.isEmpty()) {
            log.debug("Book samples of {}: no story sentences", document.id());
            return List.of();
        }
        final int storyTokens = TokenEstimator.estimate(passage(lines, new Window(0, lines.size())), languageTag);
        final int count = Math.max(1, Math.min(windows, storyTokens / windowTokens));
        log.debug(
                "Book samples of {}: {} story sentences, {} tokens, {} of {} wanted windows of {} tokens",
                document.id(),
                lines.size(),
                storyTokens,
                count,
                windows,
                windowTokens);
        final List<Window> placed = placed(lines, languageTag, count, windowTokens);
        final List<BookSample> samples = placed.stream()
                .map(window -> sampleOf(lines, window, languageTag, locators))
                .toList();
        log.debug("Book samples of {}: windows {}, dialogue shares {}", document.id(), placed, shares(samples));
        return samples;
    }

    // A window that starts at the story's first sentence and ends at its last holds the whole story.
    private static BookSample sampleOf(
            final List<Line> lines,
            final Window window,
            final @Nullable String languageTag,
            final Map<String, SegmentLocator> locators) {
        final String text = passage(lines, window);
        final boolean whole = window.from() == 0 && window.to() == lines.size();
        final String where = location(
                locators,
                lines.get(window.from()).segmentId(),
                lines.get(window.to() - 1).segmentId());
        return new BookSample(List.of(text), dialoguePercent(text, languageTag), whole, languageTag, where);
    }

    // "ch1 · p02–p14" inside one chapter, "ch1 · p30 – ch2 · p05" across two, one locator for a one-paragraph window.
    private static String location(
            final Map<String, SegmentLocator> locators, final String firstId, final String lastId) {
        final String first =
                locators.getOrDefault(firstId, new SegmentLocator("")).text();
        final String last =
                locators.getOrDefault(lastId, new SegmentLocator("")).text();
        if (first.equals(last) || last.isEmpty()) {
            return first;
        }
        final int firstCut = first.indexOf(LOCATOR_SEPARATOR);
        final int lastCut = last.indexOf(LOCATOR_SEPARATOR);
        final boolean sameChapter =
                firstCut > 0 && lastCut > 0 && first.substring(0, firstCut).equals(last.substring(0, lastCut));
        return sameChapter ? first + "–" + last.substring(lastCut + LOCATOR_SEPARATOR.length()) : first + " – " + last;
    }

    // The first window starts at the story's first sentence and the last ends at its last; the others are spread
    // evenly between, each starting at a paragraph's start and never before the end of the window before it.
    private static List<Window> placed(
            final List<Line> lines, @Nullable final String languageTag, final int count, final int windowTokens) {
        final int lastStart = count == 1 ? 0 : startOfLast(lines, languageTag, windowTokens);
        final List<Window> placed = new ArrayList<>();
        int previousEnd = 0;
        for (int index = 0; index < count; index++) {
            final int target = count == 1 ? 0 : (int) ((long) index * lastStart / (count - 1));
            final int start = Math.max(previousEnd, paragraphStart(lines, target));
            if (start >= lines.size()) {
                log.debug("Book samples: window {} starts past the story's end and is dropped", index + 1);
                break;
            }
            final Window window = new Window(start, endOfWindow(lines, languageTag, start, windowTokens));
            placed.add(window);
            previousEnd = window.to();
        }
        return placed;
    }

    // The latest start from which a window still reaches the story's end within the budget.
    private static int startOfLast(final List<Line> lines, @Nullable final String languageTag, final int budget) {
        int low = 0;
        int high = lines.size() - 1;
        while (low < high) {
            final int middle = (low + high) / 2;
            if (TokenEstimator.estimate(passage(lines, new Window(middle, lines.size())), languageTag) <= budget) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    // The most sentences from start that fit the budget, at least one.
    private static int endOfWindow(
            final List<Line> lines, @Nullable final String languageTag, final int start, final int budget) {
        int low = start + 1;
        int high = Math.min(lines.size(), start + budget);
        while (low < high) {
            final int middle = (low + high + 1) / 2;
            if (TokenEstimator.estimate(passage(lines, new Window(start, middle)), languageTag) <= budget) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static List<Integer> shares(final List<BookSample> samples) {
        return samples.stream().map(BookSample::dialoguePercent).toList();
    }

    /**
     * Every body paragraph of the book as display text, in reading order.
     *
     * @param document the opened book
     * @return the paragraphs; never null, empty when the book has no body text
     */
    static List<String> paragraphs(final Document document) {
        Objects.requireNonNull(document, "document");
        return chapters(document).stream().flatMap(List::stream).map(Para::text).toList();
    }

    private static List<List<Para>> chapters(final Document document) {
        final List<List<Para>> chapters = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary() || unit.role() != UnitRole.BODY) {
                log.trace("Book samples skip unit {} role {}", unit.id(), unit.role());
                continue;
            }
            List<Para> current = new ArrayList<>();
            for (final Segment segment : unit.segments()) {
                if (segment.kind() == SegmentKind.HEADING || segment.kind() == SegmentKind.TITLE) {
                    keep(chapters, current);
                    current = new ArrayList<>();
                } else if (segment.kind() == SegmentKind.PARAGRAPH) {
                    final String text = DisplayText.of(segment.masked());
                    if (!text.isEmpty()) {
                        current.add(new Para(segment.id(), text));
                    }
                }
            }
            keep(chapters, current);
        }
        return chapters;
    }

    private static void keep(final List<List<Para>> chapters, final List<Para> paragraphs) {
        if (!paragraphs.isEmpty()) {
            chapters.add(paragraphs);
        }
    }

    // A converter's page or a contents list has short lines and no paragraph of full sentences; when nothing in the
    // book is story by that measure, the whole body is read without its short lines rather than nothing at all.
    private static List<List<Para>> storyChapters(final List<List<Para>> chapters) {
        final List<List<Para>> story = chapters.stream()
                .filter(paragraphs ->
                        StoryMeasure.isStory(paragraphs.stream().map(Para::text).toList()))
                .toList();
        log.debug("Book samples: {} of {} chapters are story", story.size(), chapters.size());
        if (!story.isEmpty()) {
            return story;
        }
        return chapters.stream()
                .map(paragraphs -> paragraphs.stream()
                        .filter(para -> para.text().length() >= MIN_FALLBACK_CHARS)
                        .toList())
                .filter(paragraphs -> !paragraphs.isEmpty())
                .toList();
    }

    private static List<Line> lines(final List<List<Para>> chapters) {
        final List<Line> lines = new ArrayList<>();
        int paragraph = 0;
        for (final List<Para> chapter : chapters) {
            for (final Para para : chapter) {
                for (final String sentence : Sentences.split(para.text())) {
                    lines.add(new Line(paragraph, sentence, para.segmentId()));
                }
                paragraph++;
            }
        }
        return lines;
    }

    private static int paragraphStart(final List<Line> lines, final int at) {
        int start = at;
        while (start > 0 && lines.get(start - 1).paragraph() == lines.get(at).paragraph()) {
            start--;
        }
        return start;
    }

    private static String passage(final List<Line> lines, final Window window) {
        final StringBuilder text = new StringBuilder();
        int paragraph = -1;
        for (int index = window.from(); index < window.to(); index++) {
            final Line line = lines.get(index);
            if (paragraph >= 0) {
                text.append(line.paragraph() == paragraph ? " " : "\n");
            }
            text.append(line.text());
            paragraph = line.paragraph();
        }
        return text.toString();
    }

    private static int dialoguePercent(final String passage, @Nullable final String languageTag) {
        long letters = 0;
        long spoken = 0;
        for (final String paragraph : passage.lines().toList()) {
            final String narration = NarratorDetector.narration(paragraph, languageTag);
            letters += paragraph.chars().filter(Character::isLetter).count();
            spoken += narration.length() == paragraph.length() ? spokenLetters(paragraph, narration) : 0;
        }
        return letters == 0 ? 0 : (int) Math.round((double) spoken * PERCENT / letters);
    }

    // The letters the narration blanked: what the characters say.
    private static long spokenLetters(final String paragraph, final String narration) {
        long count = 0;
        for (int index = 0; index < paragraph.length(); index++) {
            if (Character.isLetter(paragraph.charAt(index)) && narration.charAt(index) == ' ') {
                count++;
            }
        }
        return count;
    }
}
