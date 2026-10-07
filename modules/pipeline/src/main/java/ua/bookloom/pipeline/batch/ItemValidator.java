package ua.bookloom.pipeline.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.ControlCharacters;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.SentenceCount;
import ua.bookloom.pipeline.qa.LengthBand;

/**
 * The per-item checks of a batch: the placeholder tokens of the source, in its order, and the length band of the
 * language pair. The document gate that pairs tokens with their words runs later on the unmasked text; this is the
 * cheap check that decides whether an item is worth passing on, so a failing id falls back on its own.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ItemValidator {

    private static final double MERGE_FACTOR = 1.5;
    // The same floor the echo check of the quality loop uses: a shorter copy may be a name or an interjection.
    private static final int ECHO_FLOOR_CODE_POINTS = 20;
    // A note marker a model drops silently because no placeholder protects it: [3], [12].
    private static final Pattern NOTE_MARKER = Pattern.compile("\\[\\d+]");

    /**
     * Checks one target against its source.
     *
     * @param item the non-null batch item
     * @param target the non-null text the reply gave under the item's id
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the non-null target language tag
     * @return the problems found; never null, empty when the target passes
     */
    static List<ItemProblem> validate(
            final BatchItem item, final String target, @Nullable final String sourceTag, final String targetTag) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(target, "target");
        final List<ItemProblem> problems = new ArrayList<>();
        if (!tokensKept(item.masked(), target)) {
            problems.add(ItemProblem.TOKENS);
        }
        if (!markers(item.masked()).equals(markers(target))) {
            problems.add(ItemProblem.MARKERS);
        }
        if (isEcho(item.masked(), target)) {
            problems.add(ItemProblem.ECHO);
        }
        if (ControlCharacters.containsControl(target)) {
            problems.add(ItemProblem.CONTROL_CHARACTERS);
        }
        if (ProtocolLeak.leaks(target)) {
            problems.add(ItemProblem.LEAKED);
        }
        lengthProblem(item.masked(), target, sourceTag, targetTag).ifPresent(problems::add);
        if (SentenceCount.dropsSentence(DisplayText.of(item.masked()), DisplayText.of(target), sourceTag, targetTag)) {
            problems.add(ItemProblem.SENTENCES);
        }
        log.debug("Validated batch item id={} problems={}", item.id(), problems);
        return problems;
    }

    private static List<String> markers(final String text) {
        return NOTE_MARKER.matcher(text).results().map(MatchResult::group).toList();
    }

    private static boolean isEcho(final String masked, final String target) {
        final String source = DisplayText.of(masked);
        return source.codePointCount(0, source.length()) >= ECHO_FLOOR_CODE_POINTS
                && source.equalsIgnoreCase(DisplayText.of(target));
    }

    private static boolean tokensKept(final String masked, final String target) {
        final String rest = Tokens.replace(target, "");
        return Tokens.inOrder(target).equals(Tokens.inOrder(masked)) && rest.indexOf('⟦') < 0 && rest.indexOf('⟧') < 0;
    }

    private static Optional<ItemProblem> lengthProblem(
            final String masked, final String target, @Nullable final String sourceTag, final String targetTag) {
        final int sourceChars = chars(masked);
        if (sourceChars == 0) {
            return Optional.empty();
        }
        final int targetChars = chars(target);
        final double ratio = (double) targetChars / sourceChars;
        final LengthBand band = LengthBand.forPair(sourceTag, targetTag).widenedFor(sourceChars);
        if (ratio > band.upper()) {
            return Optional.of(ItemProblem.TOO_LONG);
        }
        return ratio < band.lower() ? Optional.of(ItemProblem.TOO_SHORT) : Optional.empty();
    }

    /**
     * The longest a target may be, per source character, before it is suspected of carrying a neighbour as well: one
     * and a half times the pair's typical ratio, never above the band's own upper bound. The typical ratio is the
     * unwidened band's middle, so a short item — whose widened band would let two sentences pass — is still caught.
     */
    static double mergeRatio(final String masked, @Nullable final String sourceTag, final String targetTag) {
        final LengthBand band = LengthBand.forPair(sourceTag, targetTag);
        final double typical = (band.lower() + band.upper()) / 2;
        return Math.min(MERGE_FACTOR * typical, band.widenedFor(chars(masked)).upper());
    }

    /** The display-text length of a masked or plain text in characters, tokens not counted. */
    static int chars(final String text) {
        final String display = DisplayText.of(text);
        return display.codePointCount(0, display.length());
    }
}
