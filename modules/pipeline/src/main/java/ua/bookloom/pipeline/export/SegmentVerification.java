package ua.bookloom.pipeline.export;

import java.util.ArrayList;
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
    private static final int SHORT_MARKUP = 3;
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][\\w:.-]*)[^>]*>(?s:.*)");

    /**
     * Checks a re-opened book against the one written, naming every segment whose markup came back different.
     *
     * @param written the non-null book as it was handed to the writer, each segment's source placeholders intact
     * @param reopened the non-null book the written file opened as
     * @param maskedTargets each segment written with a target mapped to its masked form; a segment absent from it is
     *     expected to hold its source's markup, and one written with a target but no masked form cannot be checked and
     *     is named
     * @return the ids of the body segments whose placeholders differ, in book order and empty when none does, or
     *     {@code validation} when the body segment count differs
     */
    static Result<List<String>> verify(
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
        final List<String> mismatched = new ArrayList<>();
        for (int index = 0; index < expected.size(); index++) {
            final Segment segment = expected.get(index);
            final @Nullable String masked = expectedMasked(segment, maskedTargets);
            if (masked == null || !samePlaceholders(segment, masked, observed.get(index))) {
                mismatched.add(segment.id());
            }
        }
        log.debug("Verified body segments count={} mismatched={}", expected.size(), mismatched);
        return Result.ok(List.copyOf(mismatched));
    }

    /**
     * The failure naming the first segment whose markup still differs after its source was written in its place.
     *
     * @param written the non-null book as it was handed to the writer
     * @param segmentId the non-null id of the segment
     * @return the {@code validation} error naming the segment by its locator
     */
    static AppError placeholderMismatch(final Document written, final String segmentId) {
        final @Nullable SegmentLocator locator = SegmentLocators.of(written).get(segmentId);
        final String named = locator == null ? segmentId : locator.text();
        log.warn(
                "Export verification failed check=placeholders segment={} locator={} code={}",
                segmentId,
                named,
                ErrorCode.validation);
        return AppError.of(
                ErrorCode.validation,
                "The exported book failed validation",
                "The written book changed the formatting of " + named + ", so it was not saved.");
    }

    // A target written with no masked form recorded cannot be compared, so it is named rather than passed unchecked.
    private static @Nullable String expectedMasked(final Segment written, final Map<String, String> maskedTargets) {
        final String masked = maskedTargets.get(written.id());
        if (masked != null) {
            return masked;
        }
        if (written.targetInner() == null) {
            return written.masked();
        }
        log.debug("segment={} written with a target but no masked form; it cannot be verified", written.id());
        return null;
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
        // A placeholder that stands for nothing — a plain-text line mark — carries no markup to compare, and whether a
        // paragraph gets them depends on its line lengths, which a translation changes.
        final List<@Nullable String> expected = Tokens.inOrder(masked).stream()
                .map(token -> written.placeholders().get(token.substring(1, token.length() - 1)))
                .filter(fragment -> fragment == null || !fragment.isEmpty())
                .toList();
        final List<String> observed = reopened.placeholders().values().stream()
                .filter(fragment -> !fragment.isEmpty())
                .toList();
        log.trace("segment={} placeholders written={} reopened={}", written.id(), expected, observed);
        if (expected.size() != observed.size()) {
            log.debug(
                    "segment={} placeholder count written={} reopened={} writtenTags={} reopenedTags={}",
                    written.id(),
                    expected.size(),
                    observed.size(),
                    tagsOf(expected),
                    tagsOf(observed));
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

    /** Each fragment's tag alone — {@code <span>}, {@code </i>} — markup a log may name without any of the book's text. */
    private static List<String> tagsOf(final List<? extends @Nullable String> fragments) {
        return fragments.stream().map(SegmentVerification::tagOf).toList();
    }

    private static String tagOf(@Nullable final String fragment) {
        if (fragment == null) {
            return "?";
        }
        final Matcher tag = TAG.matcher(fragment);
        if (tag.matches()) {
            return "<" + tag.group(1) + tag.group(2) + ">";
        }
        return fragment.length() <= SHORT_MARKUP ? fragment : "#" + fragment.length();
    }

    private static List<Segment> bodySegments(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    private static AppError countMismatchError() {
        return AppError.of(
                ErrorCode.validation,
                "The exported book failed validation",
                "The written book did not contain the same number of translatable segments as the source.");
    }
}
