package ua.bookloom.pipeline.reviewer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The local label a pair carries in one reviewer call. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewerLabels {

    /**
     * The label of the pair at an index.
     *
     * @param zeroBasedIndex the pair's place in the batch
     * @return {@code s1} for the first pair, {@code s2} for the second
     */
    static String of(final int zeroBasedIndex) {
        return "s" + (zeroBasedIndex + 1);
    }
}
