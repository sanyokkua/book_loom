package ua.bookloom.pipeline.proofread;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Settles by code each open gender deferral whose fact has arrived: a segment with no word of the other gender is
 * resolved with no model call, and one with such a word keeps its deferral open, which the mark list reads as a tier-1
 * mark. Most segments that wait on a gender hold no verb that agrees with it, so this costs nothing for them.
 */
@Slf4j
@RequiredArgsConstructor
final class DeferralSettler {

    private final DeferralRepository deferrals;

    /** What the settling did: how many deferrals it resolved and how many it left open as mismatches. */
    record Settled(int resolved, int mismatches) {}

    /**
     * Settles the open deferrals of a project.
     *
     * @param projectId the non-null project id
     * @param records the project's stored records by segment id
     * @param byTerm the glossary entries by term
     * @param narrator the brief's narrator as it is now
     * @param targetLanguage the target language tag
     * @return the counts, or the storage error of a read or a resolve
     */
    Result<Settled> settle(
            final String projectId,
            final Map<String, SegmentRecord> records,
            final Map<String, GlossaryEntry> byTerm,
            final Narrator narrator,
            final String targetLanguage) {
        final Result<List<Deferral>> open = deferrals.open(projectId);
        if (open.isErr()) {
            return Result.err(Objects.requireNonNull(open.error(), ERROR));
        }
        int resolved = 0;
        int mismatches = 0;
        for (final Deferral deferral : Objects.requireNonNull(open.data(), "open")) {
            final Optional<ArrivedGender> arrived = ArrivedGender.of(deferral, byTerm, narrator);
            if (arrived.isEmpty()) {
                continue;
            }
            if (isMismatch(deferral, arrived.get(), records, targetLanguage)) {
                mismatches++;
                continue;
            }
            final Result<Boolean> done = deferrals.resolve(projectId, deferral.id());
            if (done.isErr()) {
                return Result.err(Objects.requireNonNull(done.error(), ERROR));
            }
            resolved++;
        }
        log.debug("Deferrals settled by code project={} resolved={} mismatches={}", projectId, resolved, mismatches);
        return Result.ok(new Settled(resolved, mismatches));
    }

    // A segment with no stored text is no mismatch: nothing in it can disagree, so its deferral has nothing to wait
    // for.
    private static boolean isMismatch(
            final Deferral deferral,
            final ArrivedGender arrived,
            final Map<String, SegmentRecord> records,
            final String targetLanguage) {
        final SegmentRecord record = records.get(deferral.segmentId());
        final boolean mismatch = record != null
                && record.effectiveTarget()
                        .filter(text -> arrived.mismatches(text, targetLanguage))
                        .isPresent();
        log.debug(
                "Deferral checked segmentId={} reason={} narrator={} mismatch={}",
                deferral.segmentId(),
                deferral.reason(),
                arrived.narrator(),
                mismatch);
        return mismatch;
    }
}
