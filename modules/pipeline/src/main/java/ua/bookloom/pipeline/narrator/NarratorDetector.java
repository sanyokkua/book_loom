package ua.bookloom.pipeline.narrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.pipeline.checks.QuotedSpans;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Reads who narrates a book. A chapter is first person when, outside quotes and dialogue, at least
 * {@link #CHAPTER_FIRST_SHARE} of its sentences have the language's first-person pronoun ({@code firstPersonPronouns}
 * in the language's file) in them; a chapter with fewer than {@link #MIN_SENTENCES} such sentences says too little to
 * judge. The book is first person when every judged chapter is, third person when none is, and mixed otherwise (a book
 * whose narrators alternate). A language with no pronoun data, or a book with nothing to judge, gives no answer.
 *
 * <p>Speech is blanked first, so a third-person book whose characters say "I" in quotation marks is still third person;
 * that is the whole reason the share is taken over narration only.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NarratorDetector {

    /** A chapter whose narration has at least this share of first-person sentences is told as "I". */
    static final double CHAPTER_FIRST_SHARE = 0.10;

    /** Fewer narration sentences than this in a chapter leave it unjudged. */
    static final int MIN_SENTENCES = 6;

    private static final int MIN_WORDS = 2;
    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+\\s+|\\R+");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final char STRAIGHT_QUOTE = '"';

    /** What one chapter's narration holds. */
    private record Tally(int sentences, int firstPerson) {

        double share() {
            return sentences == 0 ? 0 : (double) firstPerson / sentences;
        }

        boolean isJudged() {
            return sentences >= MIN_SENTENCES;
        }

        boolean isFirstPerson() {
            return isJudged() && share() >= CHAPTER_FIRST_SHARE;
        }
    }

    /**
     * Reads the narrator of an opened book. A chapter starts at each heading and at each body unit.
     *
     * @param document the non-null opened book; only its body units are read
     * @param languageTag the source language's tag, or null when it is not known
     * @return the hint, or empty when the language has no pronoun data or the book has no narration to judge
     */
    public static Optional<NarratorHint> detect(final Document document, @Nullable final String languageTag) {
        Objects.requireNonNull(document, "document");
        return detect(chaptersOf(document), languageTag);
    }

    /**
     * Reads the narrator of a book given as paragraphs per chapter.
     *
     * @param chapters the non-null paragraphs of each chapter, in reading order
     * @param languageTag the source language's tag, or null when it is not known
     * @return the hint, or empty when the language has no pronoun data or no chapter has enough narration to judge
     */
    public static Optional<NarratorHint> detect(final List<List<String>> chapters, @Nullable final String languageTag) {
        Objects.requireNonNull(chapters, "chapters");
        if (languageTag == null) {
            log.debug("Narrator detection skipped: the source language is not known");
            return Optional.empty();
        }
        final Set<String> pronouns = LanguageRules.bundled().firstPersonPronouns(languageTag);
        log.debug("Narrator detection language={} chapters={} pronouns={}", languageTag, chapters.size(), pronouns);
        if (pronouns.isEmpty()) {
            return Optional.empty();
        }
        final List<Tally> tallies = chapters.stream()
                .map(paragraphs -> tally(paragraphs, languageTag, pronouns))
                .toList();
        return classify(tallies);
    }

    private static Optional<NarratorHint> classify(final List<Tally> tallies) {
        final List<Integer> first = new ArrayList<>();
        for (int index = 0; index < tallies.size(); index++) {
            logChapter(index + 1, tallies.get(index));
            if (tallies.get(index).isFirstPerson()) {
                first.add(index + 1);
            }
        }
        final int judged = (int) tallies.stream().filter(Tally::isJudged).count();
        if (judged == 0) {
            log.debug("Narrator detection: no chapter has {} narration sentences", MIN_SENTENCES);
            return Optional.empty();
        }
        final NarratorHint.Person person = first.isEmpty()
                ? NarratorHint.Person.THIRD
                : first.size() == judged ? NarratorHint.Person.FIRST : NarratorHint.Person.MIXED;
        final int sentences = tallies.stream().mapToInt(Tally::sentences).sum();
        final int firstPerson = tallies.stream().mapToInt(Tally::firstPerson).sum();
        final NarratorHint hint = new NarratorHint(person, (double) firstPerson / sentences, first);
        log.info(
                "Narrator detected person={} firstPersonShare={} firstPersonChapters={} judged={}",
                person,
                hint.firstPersonShare(),
                first.size(),
                judged);
        return Optional.of(hint);
    }

    private static void logChapter(final int chapter, final Tally tally) {
        log.debug(
                "Narrator chapter={} sentences={} firstPerson={} share={}",
                chapter,
                tally.sentences(),
                tally.firstPerson(),
                tally.share());
    }

    private static Tally tally(final List<String> paragraphs, final String languageTag, final Set<String> pronouns) {
        int sentences = 0;
        int firstPerson = 0;
        for (final String paragraph : paragraphs) {
            for (final String sentence : SENTENCE_END
                    .splitAsStream(narration(paragraph, languageTag))
                    .toList()) {
                final List<String> words = words(sentence);
                if (words.size() >= MIN_WORDS) {
                    sentences++;
                    firstPerson += words.stream().anyMatch(pronouns::contains) ? 1 : 0;
                }
            }
        }
        return new Tally(sentences, firstPerson);
    }

    private static List<String> words(final String sentence) {
        final Matcher matcher = WORD.matcher(sentence);
        final List<String> words = new ArrayList<>();
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    // The language's own quote marks and a dash dialogue are blanked by QuotedSpans; straight quotes cannot be told
    // from their closing twin there, so they are paired here, and an unclosed one blanks the rest.
    private static String narration(final String paragraph, final String languageTag) {
        final String blanked = QuotedSpans.narration(TOKEN.matcher(paragraph).replaceAll(""), languageTag);
        final char[] chars = blanked.toCharArray();
        boolean inside = false;
        for (int index = 0; index < chars.length; index++) {
            final boolean quote = chars[index] == STRAIGHT_QUOTE;
            inside ^= quote;
            if (quote || inside) {
                chars[index] = ' ';
            }
        }
        return new String(chars);
    }

    private static List<List<String>> chaptersOf(final Document document) {
        final List<List<String>> chapters = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary() || unit.role() != UnitRole.BODY) {
                continue;
            }
            List<String> current = new ArrayList<>();
            for (final Segment segment : unit.segments()) {
                if (segment.kind() == SegmentKind.HEADING || segment.kind() == SegmentKind.TITLE) {
                    keep(chapters, current);
                    current = new ArrayList<>();
                } else if (segment.kind() == SegmentKind.PARAGRAPH) {
                    current.add(segment.masked());
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
}
