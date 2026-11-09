package ua.bookloom.pipeline.proofread;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * Writes a renamed pinned or locked target into the project's kept chapter summaries (design D10, "Store"), with no
 * model call, so a later window is never taught the old spelling by the story so far. A suggestion is not the person's
 * choice and changes nothing. A summary that cannot be read or saved is a warning: the rename itself stands.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SummarySweep {

    /**
     * Replaces the old target in every kept summary of the entry's project.
     *
     * @param summaries the non-null store of the kept summaries
     * @param before the non-null entry as it was
     * @param after the non-null entry as it is now
     * @param language the target language tag, for a pinned target's forms; null when unknown
     * @return how many summaries changed; zero when the change is no rename of a pinned or locked target
     */
    public static int swap(
            final SummaryRepository summaries,
            final GlossaryEntry before,
            final GlossaryEntry after,
            @Nullable final String language) {
        Objects.requireNonNull(summaries, "summaries");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        final Optional<String> skipped = notSwapped(before, after);
        if (skipped.isPresent()) {
            log.debug("Summary swap project={} entry={} skipped: {}", after.projectId(), after.id(), skipped.get());
            return 0;
        }
        final List<ChapterSummary> read = kept(summaries, after);
        int changed = 0;
        for (final ChapterSummary summary : read) {
            changed += swapOne(summaries, summary, before, after, language) ? 1 : 0;
        }
        log.debug(
                "Summary swap project={} entry={} summaries={} changed={}",
                after.projectId(),
                after.id(),
                read.size(),
                changed);
        return changed;
    }

    // A store that cannot be read is a warning and leaves every summary as it was.
    private static List<ChapterSummary> kept(final SummaryRepository summaries, final GlossaryEntry after) {
        final Result<List<ChapterSummary>> kept = summaries.list(after.projectId());
        final List<ChapterSummary> read = kept.data();
        if (read == null) {
            log.warn(
                    "Summary swap project={} entry={} not made: the summaries could not be read code={}",
                    after.projectId(),
                    after.id(),
                    Objects.requireNonNull(kept.error(), "error").code());
            return List.of();
        }
        return read;
    }

    private static Optional<String> notSwapped(final GlossaryEntry before, final GlossaryEntry after) {
        if (after.isSuggested()) {
            return Optional.of("the new target is only a suggestion");
        }
        final String old = before.target();
        final String renamed = after.target();
        if (old == null || old.isBlank()) {
            return Optional.of("there was no target before");
        }
        if (renamed == null || renamed.isBlank()) {
            return Optional.of("there is no target now");
        }
        return old.equals(renamed) ? Optional.of("the target is unchanged") : Optional.empty();
    }

    // Saving under the same key keeps the summary's place in the list and its source hash.
    private static boolean swapOne(
            final SummaryRepository summaries,
            final ChapterSummary summary,
            final GlossaryEntry before,
            final GlossaryEntry after,
            @Nullable final String language) {
        final String swapped = TargetSwap.swap(summary.text(), before, after, language);
        if (swapped.equals(summary.text())) {
            return false;
        }
        if (log.isTraceEnabled()) {
            log.trace("Summary swap chapter={} before={} after={}", summary.chapterKey(), summary.text(), swapped);
        }
        final Result<ChapterSummary> saved = summaries.save(new ChapterSummary(
                summary.projectId(),
                summary.chapterKey(),
                summary.unitId(),
                summary.firstSegment(),
                summary.segmentCount(),
                swapped,
                summary.sourceHash()));
        if (saved.isErr()) {
            log.warn(
                    "Summary swap chapter={} not saved code={}",
                    summary.chapterKey(),
                    Objects.requireNonNull(saved.error(), "error").code());
            return false;
        }
        return true;
    }
}
