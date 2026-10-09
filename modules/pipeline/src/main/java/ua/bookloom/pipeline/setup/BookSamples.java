package ua.bookloom.pipeline.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.Sentences;
import ua.bookloom.pipeline.narrator.NarratorDetector;

/**
 * Takes two disjoint samples of a book's story for the brief suggestion, so the brief is judged from the book and not
 * from its opening, and a field is kept only when both samples say the same. Each sample is three contiguous windows of
 * {@value #WINDOW} sentences: at the start of the first story chapter, in the middle of the story and at the start of a
 * late chapter; the second sample reads the {@value #WINDOW} sentences that follow each window of the first.
 *
 * <p>Story is what a chapter of the book's body holds when it has at least {@value #MIN_CHAPTER_SENTENCES} sentences
 * and a paragraph of two full sentences: front and back matter, the metadata unit, a contents list, a converter's page
 * and a heading-only unit are not read. A book with no such chapter is read whole, minus its short lines. A book too
 * short for six windows is cut into six equal ones, and one too short for that into two halves.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BookSamples {

    /** The sentences in one window. */
    static final int WINDOW = 40;

    /** A chapter with fewer sentences than this is not story. */
    static final int MIN_CHAPTER_SENTENCES = 20;

    private static final int WINDOWS = 6;
    private static final int SAMPLES = 2;
    private static final int STORY_PARAGRAPH_SENTENCES = 2;
    private static final int MIN_FALLBACK_CHARS = 40;
    private static final double LATE = 0.8;
    private static final int PERCENT = 100;

    private record Line(int chapter, int paragraph, String text) {}

    private record Window(int from, int size) {}

    /**
     * The samples of a book.
     *
     * @param document the opened book; only its body units are read
     * @param languageTag the source language, whose quote marks tell speech from narration
     * @return two disjoint samples, one for a book of a single sentence, none for a book with no story text
     */
    static List<BookSample> of(final Document document, final String languageTag) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(languageTag, "languageTag");
        final List<Line> lines = lines(storyChapters(chapters(document)));
        final List<Window> windows = windows(lines);
        log.debug("Book samples of {}: {} story sentences, windows {}", document.id(), lines.size(), windows);
        final List<BookSample> samples = new ArrayList<>();
        for (int sample = 0; sample < SAMPLES; sample++) {
            final List<String> passages = new ArrayList<>();
            for (int index = sample; index < windows.size(); index += SAMPLES) {
                passages.add(passage(lines, windows.get(index)));
            }
            passages.removeIf(String::isBlank);
            if (!passages.isEmpty()) {
                samples.add(new BookSample(passages, dialoguePercent(passages, languageTag)));
            }
        }
        log.debug("Book samples of {}: {} samples, dialogue shares {}", document.id(), samples.size(), shares(samples));
        return samples;
    }

    private static List<Integer> shares(final List<BookSample> samples) {
        return samples.stream().map(BookSample::dialoguePercent).toList();
    }

    private static List<List<String>> chapters(final Document document) {
        final List<List<String>> chapters = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary() || unit.role() != UnitRole.BODY) {
                log.trace("Book samples skip unit {} role {}", unit.id(), unit.role());
                continue;
            }
            List<String> current = new ArrayList<>();
            for (final Segment segment : unit.segments()) {
                if (segment.kind() == SegmentKind.HEADING || segment.kind() == SegmentKind.TITLE) {
                    keep(chapters, current);
                    current = new ArrayList<>();
                } else if (segment.kind() == SegmentKind.PARAGRAPH) {
                    final String text = DisplayText.of(segment.masked());
                    if (!text.isEmpty()) {
                        current.add(text);
                    }
                }
            }
            keep(chapters, current);
        }
        return chapters;
    }

    private static void keep(final List<List<String>> chapters, final List<String> paragraphs) {
        if (!paragraphs.isEmpty()) {
            chapters.add(paragraphs);
        }
    }

    // A converter's page or a contents list has short lines and no paragraph of full sentences; when nothing in the
    // book is story by that measure, the whole body is read without its short lines rather than nothing at all.
    private static List<List<String>> storyChapters(final List<List<String>> chapters) {
        final List<List<String>> story =
                chapters.stream().filter(BookSamples::isStory).toList();
        log.debug("Book samples: {} of {} chapters are story", story.size(), chapters.size());
        if (!story.isEmpty()) {
            return story;
        }
        return chapters.stream()
                .map(paragraphs -> paragraphs.stream()
                        .filter(text -> text.length() >= MIN_FALLBACK_CHARS)
                        .toList())
                .filter(paragraphs -> !paragraphs.isEmpty())
                .toList();
    }

    static boolean isStory(final List<String> paragraphs) {
        final int sentences = paragraphs.stream()
                .mapToInt(text -> Sentences.split(text).size())
                .sum();
        final boolean hasStoryParagraph =
                paragraphs.stream().anyMatch(text -> Sentences.significant(text).size() >= STORY_PARAGRAPH_SENTENCES);
        return sentences >= MIN_CHAPTER_SENTENCES && hasStoryParagraph;
    }

    private static List<Line> lines(final List<List<String>> chapters) {
        final List<Line> lines = new ArrayList<>();
        int paragraph = 0;
        for (int chapter = 0; chapter < chapters.size(); chapter++) {
            for (final String text : chapters.get(chapter)) {
                for (final String sentence : Sentences.split(text)) {
                    lines.add(new Line(chapter, paragraph, sentence));
                }
                paragraph++;
            }
        }
        return lines;
    }

    // The windows in the order first sample, second sample, for the start, the middle and the late chapter.
    private static List<Window> windows(final List<Line> lines) {
        final int total = lines.size();
        if (total >= WINDOWS * WINDOW) {
            final int middle = clamp(paragraphStart(lines, total / 2), 2 * WINDOW, total - 4 * WINDOW);
            final int late = clamp(chapterStart(lines, (int) (total * LATE)), middle + 2 * WINDOW, total - 2 * WINDOW);
            log.debug("Book samples: full windows at 0, {} and {} of {} sentences", middle, late, total);
            final List<Window> windows = new ArrayList<>();
            for (final int start : List.of(0, middle, late)) {
                windows.add(new Window(start, WINDOW));
                windows.add(new Window(start + WINDOW, WINDOW));
            }
            return windows;
        }
        final int size = total / WINDOWS;
        if (size > 0) {
            log.debug("Book samples: {} sentences make six windows of {}", total, size);
            final List<Window> windows = new ArrayList<>();
            for (int index = 0; index < WINDOWS; index++) {
                windows.add(new Window(index * size, size));
            }
            return windows;
        }
        final int half = (total + 1) / SAMPLES;
        log.debug("Book samples: {} sentences make two halves", total);
        return List.of(new Window(0, half), new Window(half, total - half));
    }

    private static int clamp(final int value, final int low, final int high) {
        return Math.max(low, Math.min(value, high));
    }

    private static int paragraphStart(final List<Line> lines, final int at) {
        int start = at;
        while (start > 0 && lines.get(start - 1).paragraph() == lines.get(at).paragraph()) {
            start--;
        }
        return start;
    }

    private static int chapterStart(final List<Line> lines, final int at) {
        int start = at;
        while (start > 0 && lines.get(start - 1).chapter() == lines.get(at).chapter()) {
            start--;
        }
        return start;
    }

    private static String passage(final List<Line> lines, final Window window) {
        final StringBuilder text = new StringBuilder();
        int paragraph = -1;
        for (int index = window.from(); index < window.from() + window.size(); index++) {
            final Line line = lines.get(index);
            if (paragraph >= 0) {
                text.append(line.paragraph() == paragraph ? " " : "\n");
            }
            text.append(line.text());
            paragraph = line.paragraph();
        }
        return text.toString();
    }

    private static int dialoguePercent(final List<String> passages, final String languageTag) {
        long letters = 0;
        long spoken = 0;
        for (final String passage : passages) {
            for (final String paragraph : passage.lines().toList()) {
                final String narration = NarratorDetector.narration(paragraph, languageTag);
                letters += paragraph.chars().filter(Character::isLetter).count();
                spoken += narration.length() == paragraph.length() ? spokenLetters(paragraph, narration) : 0;
            }
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
