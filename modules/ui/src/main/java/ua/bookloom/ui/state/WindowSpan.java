package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ProofreadItem;

/**
 * The span a proofread window covers, worded from its first and last locator: {@code ch7 · p40–p42} when both sit in
 * the same chapter, else the two locators joined by a dash. Pure; logs nothing, because the live card's cells and the
 * log feed call it on every redraw.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WindowSpan {

    private static final String PART = " · ";
    private static final String DASH = "–";

    /**
     * The span of a window's items.
     *
     * @param items the non-null, non-empty focus segments in reading order
     * @return for example {@code ch7 · p40–p42}, or the one locator of a window of one segment
     */
    public static String of(final List<ProofreadItem> items) {
        Objects.requireNonNull(items, "items");
        final String first = items.getFirst().locator();
        final String last = items.getLast().locator();
        if (first.equals(last)) {
            return first;
        }
        final int firstPart = first.lastIndexOf(PART);
        final int lastPart = last.lastIndexOf(PART);
        final boolean sameChapter = firstPart > 0
                && firstPart == lastPart
                && first.substring(0, firstPart).equals(last.substring(0, lastPart));
        return sameChapter ? first + DASH + last.substring(lastPart + PART.length()) : first + DASH + last;
    }
}
