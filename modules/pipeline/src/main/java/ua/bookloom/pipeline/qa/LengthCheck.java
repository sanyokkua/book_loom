package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.LENGTH;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The length-ratio check: whether the target's length, against its source, sits inside the pair's expected band — and
 * whether words went missing that the character count alone would not show: far fewer words than the source, or a
 * space left before a full stop or comma where a word was dropped ({@code за допомогою .}).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LengthCheck {

    private static final double MARGIN_WINDOW_FACTOR = 0.10;

    /**
     * The fewest target words per source word before words count as missing. Kept well below any real pair's ratio
     * (English to Ukrainian runs about 0.8, losing articles) so a faithful translation is never held to it.
     */
    static final double MIN_WORD_RATIO = 0.55;

    /** A source shorter than this many words swings too widely for a word ratio to mean anything. */
    static final int MIN_SOURCE_WORDS = 8;

    /**
     * The character ratio under which a low word ratio counts: a verse line Ukrainian says in half the words
     * ({@code A stone let go will find the ground,} → {@code Камінь, відпущений, знайде землю,}) keeps about its length,
     * a translation that dropped a phrase does not.
     */
    static final double MAX_CHAR_RATIO_FOR_WORDS = 0.85;

    /** A source of at most this many words, and under {@link #COMPACT_MAX_CHARS} chars, is a compact line. */
    private static final int COMPACT_MAX_WORDS = 10;

    /** The size a compact line had before 15e.8: under 40 characters and at most 8 words. */
    private static final int COMPACT_BASE_CHARS = 40;

    private static final int COMPACT_BASE_WORDS = 8;

    private static final int COMPACT_MAX_CHARS = 60;

    /**
     * A compact line is trusted only while its target keeps this share of the source's characters: below it the
     * target is a fragment however few function words the language needs.
     */
    private static final double COMPACT_MIN_CHAR_RATIO = 0.40;

    /**
     * The word share a compact line's target may fall to, with {@link #COMPACT_MIN_RELAXED_WORDS} words at least:
     * {@code Then, without a word, the old man left the room.} (10 words) → {@code Старий мовчки вийшов.} (3).
     */
    private static final double COMPACT_MIN_RELAXED_WORD_SHARE = 0.3;

    private static final int COMPACT_MIN_RELAXED_WORDS = 3;

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’-]*");

    /** A letter, spaces, then a full stop or comma that ends the text or is followed by a space: a dropped word. */
    private static final Pattern DANGLING_PUNCTUATION = Pattern.compile("\\p{L}\\s+[.,](?=\\s|$)");

    /** Scripts written without spaces between words, where a word count is not a measure of the text. */
    private static final Pattern UNSPACED_SCRIPT =
            Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsThai}\\p{IsLao}\\p{IsKhmer}]");

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        final int sourceChars = codePointCount(input.sourceDisplayText());
        if (sourceChars == 0) {
            return CheckResult.skip(LENGTH);
        }
        final int targetChars = codePointCount(input.targetDisplayText());
        final double ratio = (double) targetChars / sourceChars;
        final LengthBand band = LengthBand.forPair(input.sourceLanguage(), input.targetLanguage())
                .widenedFor(sourceChars);
        final boolean compact = isCompactLine(input.sourceDisplayText(), input.targetDisplayText(), sourceChars, ratio);
        if (ratio > band.upper() || (ratio < band.lower() && !compact)) {
            return CheckResult.fail(
                    LENGTH, "length ratio " + ratio + " outside [" + band.lower() + "," + band.upper() + "]");
        }
        final Optional<String> omission = omission(
                input.sourceDisplayText(),
                input.targetDisplayText(),
                ratio,
                compact && isBeyondBaseSize(sourceChars, count(WORD, input.sourceDisplayText())));
        return omission.isPresent()
                ? CheckResult.fail(LENGTH, omission.get())
                : CheckResult.pass(LENGTH, ratio < band.lower() ? 1.0 : marginWithinBand(ratio, band));
    }

    /**
     * Whether a short source is a compact line — {@code Not now, boy.}, {@code Unfortunately, nothing happened.} — that
     * a language with fewer function words says in about half the characters, complete: its ratio below the band says
     * nothing (14 faithful short lines were flagged as omissions in one real run, six more in a 6-hour one). It is
     * compact only while the target keeps at least half of the source's words; a phrase cut to a fragment is still an
     * omission. A line past the first 40 characters or 8 words is held to more: its target keeps at least
     * {@value #COMPACT_MIN_CHAR_RATIO} of the characters and at least three words and
     * {@value #COMPACT_MIN_RELAXED_WORD_SHARE} of the source's words.
     */
    private static boolean isCompactLine(
            final String source, final String target, final int sourceChars, final double charRatio) {
        final int sourceWords = count(WORD, source);
        final int targetWords = count(WORD, target);
        final boolean extended = isBeyondBaseSize(sourceChars, sourceWords);
        final boolean keepsWords = 2 * targetWords >= sourceWords
                || (extended
                        && targetWords >= COMPACT_MIN_RELAXED_WORDS
                        && targetWords >= COMPACT_MIN_RELAXED_WORD_SHARE * sourceWords);
        final boolean compact = sourceChars < COMPACT_MAX_CHARS
                && sourceWords <= COMPACT_MAX_WORDS
                && (!extended || charRatio >= COMPACT_MIN_CHAR_RATIO)
                && keepsWords;
        if (compact) {
            log.debug("Length check: compact line ({} chars, {} words)", sourceChars, sourceWords);
        }
        return compact;
    }

    private static boolean isBeyondBaseSize(final int sourceChars, final int sourceWords) {
        return sourceChars >= COMPACT_BASE_CHARS || sourceWords > COMPACT_BASE_WORDS;
    }

    /** The note on words missing from {@code target}, or empty when none look missing. */
    private static Optional<String> omission(
            final String source, final String target, final double charRatio, final boolean exemptWordCount) {
        final int dangling = count(DANGLING_PUNCTUATION, target) - count(DANGLING_PUNCTUATION, source);
        if (dangling > 0) {
            log.debug("Length check: {} space(s) before punctuation the source does not have", dangling);
            return Optional.of("a word is missing before a full stop or comma: a space is left where it stood");
        }
        // A longer compact line (past the old size) has said what it keeps in few words; its word count is no sign
        // of a lost phrase.
        if (exemptWordCount || !isWordProse(source) || !isWordProse(target)) {
            return Optional.empty();
        }
        final int sourceWords = count(WORD, source);
        final int targetWords = count(WORD, target);
        final boolean missing = sourceWords >= MIN_SOURCE_WORDS
                && targetWords < MIN_WORD_RATIO * sourceWords
                && charRatio < MAX_CHAR_RATIO_FOR_WORDS;
        log.debug(
                "Length check words source={} target={} charRatio={} missing={}",
                sourceWords,
                targetWords,
                charRatio,
                missing);
        return missing
                ? Optional.of("only " + targetWords + " words for a source of " + sourceWords
                        + " — words or a phrase may be missing")
                : Optional.empty();
    }

    /**
     * Whether a word count measures the text: mostly letters, in a script that puts spaces between words — a table
     * row of figures, or Chinese, says nothing by its word count.
     */
    private static boolean isWordProse(final String text) {
        final long visible = text.codePoints()
                .filter(point -> !Character.isWhitespace(point))
                .count();
        final long letters = text.codePoints().filter(Character::isLetter).count();
        return letters * 2 >= visible && !UNSPACED_SCRIPT.matcher(text).find();
    }

    private static int count(final Pattern pattern, final String text) {
        final Matcher matcher = pattern.matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private static double marginWithinBand(final double ratio, final LengthBand band) {
        final double distanceToNearerBound = Math.min(ratio - band.lower(), band.upper() - ratio);
        final double window = MARGIN_WINDOW_FACTOR * (band.upper() - band.lower());
        return Margins.clamp(distanceToNearerBound / window);
    }

    private static int codePointCount(final String text) {
        return text.codePointCount(0, text.length());
    }
}
