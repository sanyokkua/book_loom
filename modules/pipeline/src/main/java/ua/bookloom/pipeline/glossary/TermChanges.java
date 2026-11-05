package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.revision.DeferralRegister;
import ua.bookloom.pipeline.revision.SummarySweep;

/**
 * What a target the person changed leaves behind, the same for an edit of the cell and a row of a glossary file: a
 * term deferral for each decided segment that used the old target, and the new target written into the kept chapter
 * summaries.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermChanges {

    /** Where a changed target is recorded. */
    record Stores(DeferralRepository deferrals, SummaryRepository summaries) {

        Stores {
            Objects.requireNonNull(deferrals, "deferrals");
            Objects.requireNonNull(summaries, "summaries");
        }
    }

    /**
     * Records one changed target.
     *
     * @param stores the non-null stores the deferrals and the summaries are written to
     * @param before the non-null entry as it was
     * @param after the non-null entry as it is now
     * @param decided the non-null decided segments of the project
     * @param targetLanguage the project's target language, for a pinned target's forms; null when none is chosen
     * @return how many deferrals were added, or the first failed write's error; a summary not rewritten is a warning
     */
    static Result<Integer> record(
            final Stores stores,
            final GlossaryEntry before,
            final GlossaryEntry after,
            final List<SegmentRecord> decided,
            @Nullable final String targetLanguage) {
        Objects.requireNonNull(stores, "stores");
        int added = 0;
        for (final Deferral deferral : DeferralRegister.termChanged(before, after, decided)) {
            final Result<Deferral> stored = stores.deferrals().add(deferral);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            added++;
        }
        final int swapped = SummarySweep.swap(stores.summaries(), before, after, targetLanguage);
        log.debug("Term change entry={} deferrals={} summariesSwapped={}", after.id(), added, swapped);
        return Result.ok(added);
    }
}
