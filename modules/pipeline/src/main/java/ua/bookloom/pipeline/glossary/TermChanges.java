package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.proofread.DeferralRegister;
import ua.bookloom.pipeline.proofread.SummarySweep;

/**
 * What a glossary write leaves behind, the same for an edit of the cell, a row of a glossary file and the names pass:
 * the term and gender deferrals on the decided segments, and a new target written into the kept chapter summaries.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermChanges {

    /** Where a change is recorded. */
    record Stores(DeferralRepository deferrals, SummaryRepository summaries, OpenProjects openProjects) {

        Stores {
            Objects.requireNonNull(deferrals, "deferrals");
            Objects.requireNonNull(summaries, "summaries");
            Objects.requireNonNull(openProjects, "openProjects");
        }
    }

    /**
     * Whether a write can leave anything behind on decided segments, so a writer reads them only then.
     *
     * @param before the non-null entry as it was
     * @param after the non-null entry as it is now
     * @return {@code true} if the target changed or an enforced gender arrived
     */
    static boolean touchesDecided(final GlossaryEntry before, final GlossaryEntry after) {
        return !Objects.equals(before.target(), after.target()) || DeferralRegister.genderArrived(before, after);
    }

    /**
     * Records the person's write of one entry, reading the decided segments only when the write can touch them.
     *
     * @param stores the non-null stores the deferrals and the summaries are written to
     * @param segments the non-null store the decided segments are read from
     * @param before the non-null entry as it was
     * @param after the non-null entry as it is now
     * @param targetLanguage the project's target language; null when none is chosen
     * @return the stored entry, or the first failed read's or write's error
     */
    static Result<GlossaryEntry> recordWrite(
            final Stores stores,
            final SegmentRepository segments,
            final GlossaryEntry before,
            final GlossaryEntry after,
            @Nullable final String targetLanguage) {
        if (!touchesDecided(before, after)) {
            return Result.ok(after);
        }
        final Result<List<SegmentRecord>> decided = segments.all(after.projectId());
        if (decided.isErr()) {
            return Result.err(Objects.requireNonNull(decided.error(), "error"));
        }
        return record(stores, before, after, Objects.requireNonNull(decided.data(), "decided"), targetLanguage)
                .map(added -> after);
    }

    /**
     * Records one written entry: its deferrals, and a changed target in the kept chapter summaries.
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
        final Result<Integer> added =
                defer(stores.deferrals(), stores.openProjects(), before, after, decided, targetLanguage);
        if (added.isErr() || Objects.equals(before.target(), after.target())) {
            return added;
        }
        final int swapped = SummarySweep.swap(stores.summaries(), before, after, targetLanguage);
        log.debug("Term change entry={} deferrals={} summariesSwapped={}", after.id(), added.data(), swapped);
        return added;
    }

    /**
     * Stores the deferrals one written entry leaves; the one place the term and the gender kinds are produced for a
     * glossary writer.
     *
     * @param deferrals where the deferrals are added; never null
     * @param openProjects where the naming segments are read from; never null
     * @param before the non-null entry as it was
     * @param after the non-null entry as it is now
     * @param decided the non-null decided segments of the project
     * @param targetLanguage the project's target language; null when none is chosen
     * @return how many deferrals were offered to the store, or the first failed write's error
     */
    static Result<Integer> defer(
            final DeferralRepository deferrals,
            final OpenProjects openProjects,
            final GlossaryEntry before,
            final GlossaryEntry after,
            final List<SegmentRecord> decided,
            @Nullable final String targetLanguage) {
        final Set<String> naming =
                DeferralRegister.genderArrived(before, after) ? SegmentMentions.naming(openProjects, after) : Set.of();
        int added = 0;
        for (final Deferral deferral : DeferralRegister.changed(before, after, decided, naming, targetLanguage)) {
            final Result<Deferral> stored = deferrals.add(deferral);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            added++;
        }
        if (added > 0) {
            log.info("Glossary change entry={} recorded {} deferrals", after.id(), added);
        }
        return Result.ok(added);
    }
}
