package ua.bookloom.pipeline.proofread;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ProofreadItem;
import ua.bookloom.api.pipeline.ProofreadWindowRead;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;

/**
 * The event that tells the window what one proofread window decided (design D15 "Visible"): each focus segment with its
 * text before and after, its verdict and the marks it was read for. A segment whose earlier rewrite was kept and that
 * this window left as it was is told as unchanged here — its stored verdict carries the earlier KEPT for Undo
 * proofread, but this window changed nothing. A proposal shows the proposed text as the text after.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowNotice {

    /**
     * The event of a decided window.
     *
     * @param question the non-null window's question, whose texts are the ones it read
     * @param decided the non-null decided records of the read focus segments, in reading order, and the proposals
     * @param locators the non-null locator of every segment of the book
     * @return the event; never null
     */
    static ProofreadWindowRead of(
            final WindowReader.Question question,
            final WindowVerdicts.Decided decided,
            final Map<String, SegmentLocator> locators) {
        final Map<String, String> proposed = new HashMap<>();
        decided.proposals().forEach(proposal -> {
            final @Nullable String masked = proposal.maskedProposal();
            if (masked != null) {
                proposed.put(proposal.segmentId(), DisplayText.of(masked));
            }
        });
        final List<ProofreadItem> items = decided.records().stream()
                .map(record -> item(question, record, locators, proposed))
                .toList();
        log.debug("Proofread window {} announced items={}", question.window().label(), items.size());
        return new ProofreadWindowRead(question.round(), items);
    }

    private static ProofreadItem item(
            final WindowReader.Question question,
            final SegmentRecord record,
            final Map<String, SegmentLocator> locators,
            final Map<String, String> proposed) {
        final String id = record.segmentId();
        final String before = DisplayText.of(question.texts().current(id));
        final @Nullable String now = MarkList.maskedTarget(record);
        final String after = proposed.getOrDefault(id, now == null ? before : DisplayText.of(now));
        final ProofreadVerdict verdict = Objects.requireNonNull(record.proofread(), "proofread");
        final boolean changed = !before.equals(after);
        // A proposal leaves the text in place; the after column shows what the person is asked to accept.
        final ProofreadOutcome outcome =
                verdict.outcome() == ProofreadOutcome.KEPT && !changed ? ProofreadOutcome.UNCHANGED : verdict.outcome();
        final @Nullable SegmentLocator locator = locators.get(id);
        return new ProofreadItem(
                id,
                locator == null ? id : locator.text(),
                before,
                after,
                outcome,
                outcome == ProofreadOutcome.UNCHANGED ? "" : verdict.reason(),
                question.window().reasons().getOrDefault(id, List.of()));
    }
}
