package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;

/**
 * Writes a finished review's verdicts to the glossary, each against the entry as it is now, not as it was asked about,
 * so an edit the person made while the model was thinking is never overwritten.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewCommit {

    /** What one verdict did. */
    private enum Outcome {
        REMOVED,
        UPDATED,
        KEPT
    }

    static Result<GlossaryReviewReport> apply(
            final GlossaryRepository glossary, final String projectId, final List<Verdict> verdicts) {
        final Result<List<GlossaryEntry>> current = glossary.all(projectId);
        if (current.isErr()) {
            return Result.err(Objects.requireNonNull(current.error(), "error"));
        }
        final Map<String, GlossaryEntry> byId = Objects.requireNonNull(current.data(), "current").stream()
                .collect(Collectors.toMap(GlossaryEntry::id, Function.identity()));
        int removed = 0;
        int updated = 0;
        for (final Verdict verdict : verdicts) {
            final GlossaryEntry now = byId.get(verdict.entry().id());
            final Result<Outcome> outcome = now == null ? Result.ok(Outcome.KEPT) : applyOne(glossary, now, verdict);
            if (outcome.isErr()) {
                return Result.err(Objects.requireNonNull(outcome.error(), "error"));
            }
            removed += outcome.data() == Outcome.REMOVED ? 1 : 0;
            updated += outcome.data() == Outcome.UPDATED ? 1 : 0;
        }
        log.info(
                "Glossary review finished project={} verdicts={} removed={} updated={}",
                projectId,
                verdicts.size(),
                removed,
                updated);
        final int removedCount = removed;
        final int updatedCount = updated;
        return glossary.all(projectId).map(after -> new GlossaryReviewReport(removedCount, updatedCount, after));
    }

    private static Result<Outcome> applyOne(
            final GlossaryRepository glossary, final GlossaryEntry now, final Verdict verdict) {
        if (!TermReview.isOpen(now)) {
            log.debug("Glossary review left entry {} alone: locked or given a target meanwhile", now.id());
            return Result.ok(Outcome.KEPT);
        }
        final boolean untouched = now.type() == TermType.OTHER && now.gender() == Gender.UNKNOWN;
        if (verdict.kind() == Kind.NOT_A_NAME && untouched) {
            log.debug("Glossary review removes entry {}: not a name", now.id());
            log.trace("Glossary review removes {}", now.term());
            return glossary.remove(now.projectId(), now.id()).map(gone -> gone ? Outcome.REMOVED : Outcome.KEPT);
        }
        final GlossaryEntry guessed = guessed(now, verdict);
        if (guessed.equals(now)) {
            log.debug("Glossary review kept entry {} as it is: verdict {}", now.id(), verdict.kind());
            return Result.ok(Outcome.KEPT);
        }
        log.debug("Glossary review sets entry {} type={} gender={}", now.id(), guessed.type(), guessed.gender());
        return glossary.update(guessed).map(stored -> Outcome.UPDATED);
    }

    private static GlossaryEntry guessed(final GlossaryEntry now, final Verdict verdict) {
        final TermType type = now.type() == TermType.OTHER ? verdict.type() : now.type();
        final Gender gender = now.gender() == Gender.UNKNOWN ? verdict.gender() : now.gender();
        return new GlossaryEntry(now.id(), now.projectId(), now.term(), now.target(), type, gender, now.locked());
    }
}
