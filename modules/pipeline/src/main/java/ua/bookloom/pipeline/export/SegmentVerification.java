package ua.bookloom.pipeline.export;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.project.SegmentLocators;

/**
 * The re-open check of a written book: its body segments' count, then per segment the placeholder markup written into
 * it — the same fragments in the same order — so a paragraph whose formatting a translation damaged never slips
 * through a count that still matches. An image's description is set aside before comparing, because it is translated
 * on its own as auxiliary text yet sits inside the paragraph's markup. The auxiliary unit is not checked: its segments
 * can legitimately change on re-opening — an NCX label translated like its navigation label merges with it, and a
 * frontmatter value written as {@code "1984"} holds no letter.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentVerification {

    private static final Pattern XML_ALT = Pattern.compile("(\\salt\\s*=\\s*)(\"[^\"]*\"|'[^']*')");
    private static final Pattern MARKDOWN_ALT = Pattern.compile("!\\[.*?]\\(", Pattern.DOTALL);
    private static final String EMPTY_MARKDOWN_ALT = "![](";

    /**
     * Checks a re-opened book against the one written.
     *
     * @param written the non-null book as it was handed to the writer, each segment's source placeholders intact
     * @param reopened the non-null book the written file opened as
     * @param maskedTargets each segment written with a target mapped to its masked form; a segment absent from it was
     *     written in its source
     * @return the number of body segments verified, or {@code validation} naming the count mismatch or the first
     *     segment whose placeholders differ by its locator
     */
    static Result<Integer> verify(
            final Document written, final Document reopened, final Map<String, String> maskedTargets) {
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(reopened, "reopened");
        Objects.requireNonNull(maskedTargets, "maskedTargets");
        final List<Segment> expected = bodySegments(written);
        final List<Segment> observed = bodySegments(reopened);
        log.debug("Verifying body segments written={} reopened={}", expected.size(), observed.size());
        if (expected.size() != observed.size()) {
            log.warn(
                    "Export verification failed check=body-count written={} reopened={} code={}",
                    expected.size(),
                    observed.size(),
                    ErrorCode.validation);
            return Result.err(countMismatchError());
        }
        final Map<String, SegmentLocator> locators = SegmentLocators.of(written);
        for (int index = 0; index < expected.size(); index++) {
            final Segment segment = expected.get(index);
            final String masked = maskedTargets.getOrDefault(segment.id(), segment.masked());
            if (!samePlaceholders(segment, masked, observed.get(index))) {
                return Result.err(placeholderMismatch(segment, locators.get(segment.id())));
            }
        }
        log.debug("Verified body segments count={}", expected.size());
        return Result.ok(expected.size());
    }

    /**
     * Sets aside every image description a placeholder's markup holds.
     *
     * @param fragment the non-null markup one placeholder stands for
     * @return the markup with each {@code alt} attribute's value, and each Markdown image's text between {@code ![}
     *     and {@code ](}, emptied
     */
    static String withoutAltText(final String fragment) {
        Objects.requireNonNull(fragment, "fragment");
        final String xml = XML_ALT.matcher(fragment)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1)
                        + match.group(2).charAt(0)
                        + match.group(2).charAt(0)));
        return MARKDOWN_ALT.matcher(xml).replaceAll(Matcher.quoteReplacement(EMPTY_MARKDOWN_ALT));
    }

    private static boolean samePlaceholders(final Segment written, final String masked, final Segment reopened) {
        final List<@Nullable String> expected = Tokens.inOrder(masked).stream()
                .map(token -> written.placeholders().get(token.substring(1, token.length() - 1)))
                .toList();
        final List<String> observed = List.copyOf(reopened.placeholders().values());
        log.trace("segment={} placeholders written={} reopened={}", written.id(), expected, observed);
        if (expected.size() != observed.size()) {
            log.debug(
                    "segment={} placeholder count written={} reopened={}",
                    written.id(),
                    expected.size(),
                    observed.size());
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            final String fragment = expected.get(index);
            if (fragment == null || !withoutAltText(fragment).equals(withoutAltText(observed.get(index)))) {
                log.debug("segment={} placeholder {} differs after setting alt text aside", written.id(), index);
                return false;
            }
        }
        return true;
    }

    private static List<Segment> bodySegments(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    private static AppError placeholderMismatch(final Segment segment, @Nullable final SegmentLocator locator) {
        final String named = locator == null ? segment.id() : locator.text();
        log.warn(
                "Export verification failed check=placeholders segment={} locator={} code={}",
                segment.id(),
                named,
                ErrorCode.validation);
        return AppError.of(
                ErrorCode.validation,
                "The exported book failed validation",
                "The written book changed the formatting of " + named + ", so it was not saved.");
    }

    private static AppError countMismatchError() {
        return AppError.of(
                ErrorCode.validation,
                "The exported book failed validation",
                "The written book did not contain the same number of translatable segments as the source.");
    }
}
