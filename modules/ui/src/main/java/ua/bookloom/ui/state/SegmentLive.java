package ua.bookloom.ui.state;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.SegmentPath;

/**
 * What the run knows about one segment of a shown call besides the call itself: the target it has so far and how it
 * was decided.
 *
 * @param target the draft's or the decided display text, or {@code null} until a draft arrives
 * @param judgeScore the judge's score, or {@code null} when the judge did not run or has not decided yet
 * @param path how the segment reached its target, or {@code null} while it is undecided
 */
public record SegmentLive(
        @Nullable String target,
        @Nullable Double judgeScore,
        @Nullable SegmentPath path) {}
