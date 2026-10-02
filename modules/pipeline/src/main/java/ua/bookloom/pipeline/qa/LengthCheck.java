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
        if (ratio < band.lower() || ratio > band.upper()) {
            return CheckResult.fail(
                    LENGTH, "length ratio " + ratio + " outside [" + band.lower() + "," + band.upper() + "]");
        }
        final Optional<String> omission = omission(input.sourceDisplayText(), input.targetDisplayText());
        return omission.isPresent()
                ? CheckResult.fail(LENGTH, omission.get())
                : CheckResult.pass(LENGTH, marginWithinBand(ratio, band));
    }

    /** The note on words missing from {@code target}, or empty when none look missing. */
    private static Optional<String> omission(final String source, final String target) {
        final int dangling = count(DANGLING_PUNCTUATION, target) - count(DANGLING_PUNCTUATION, source);
        if (dangling > 0) {
            log.debug("Length check: {} space(s) before punctuation the source does not have", dangling);
            return Optional.of("a word is missing before a full stop or comma: a space is left where it stood");
        }
        if (!isWordProse(source) || !isWordProse(target)) {
            return Optional.empty();
        }
        final int sourceWords = count(WORD, source);
        final int targetWords = count(WORD, target);
        final boolean missing = sourceWords >= MIN_SOURCE_WORDS && targetWords < MIN_WORD_RATIO * sourceWords;
        log.debug("Length check words source={} target={} missing={}", sourceWords, targetWords, missing);
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
