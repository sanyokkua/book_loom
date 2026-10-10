package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;

/**
 * Writes a finished review's verdicts and suggestions to the glossary, each against the entry as it is now, not as it
 * was asked about, so an edit the person made while the model was thinking is never overwritten: a locked entry or one
 * whose target the person chose is left alone, and only an empty target or an earlier suggestion takes a suggestion.
 * Writes go straight to the repository, not through the service, so a suggestion records no deferral.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewCommit {

    /** The entries the review touched, by what it did to them. */
    private record Tally(Set<String> removed, Set<String> updated, Set<String> suggested) {

        static Tally empty() {
            return new Tally(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    /**
     * The open entries as the verdicts would leave them: without those judged not a name and never set, and with the
     * model's type and gender where theirs were still unset — what the suggestion step is asked about.
     */
    static List<GlossaryEntry> survivors(final List<GlossaryEntry> open, final List<Verdict> verdicts) {
        final Map<String, Verdict> byId = new HashMap<>();
        verdicts.forEach(verdict -> byId.putIfAbsent(verdict.entry().id(), verdict));
        final List<GlossaryEntry> kept = new ArrayList<>();
        for (final GlossaryEntry entry : open) {
            final Verdict verdict = byId.get(entry.id());
            if (verdict == null) {
                kept.add(entry);
            } else if (!removes(entry, verdict)) {
                kept.add(guessed(entry, verdict));
            }
        }
        log.debug("Glossary review: {} open entries, {} survive the verdicts", open.size(), kept.size());
        return kept;
    }

    static Result<GlossaryReviewReport> apply(
            final GlossaryRepository glossary,
            final String projectId,
            final List<Verdict> verdicts,
            final List<Suggestion> suggestions,
            @Nullable final String sourceLanguage,
            final List<String> bookTexts) {
        final Result<List<GlossaryEntry>> current = glossary.all(projectId);
        if (current.isErr()) {
            return Result.err(Objects.requireNonNull(current.error(), "error"));
        }
        final Map<String, GlossaryEntry> byId = new HashMap<>(Objects.requireNonNull(current.data(), "current").stream()
                .collect(Collectors.toMap(GlossaryEntry::id, Function.identity())));
        final Tally tally = Tally.empty();
        final Result<Boolean> applied = applyAll(glossary, byId, verdicts, suggestions, tally)
                .flatMap(done -> GivenNames.seedHeld(glossary, projectId, sourceLanguage)
                        .flatMap(seeded -> PronounGender.seedHeld(glossary, projectId, bookTexts, sourceLanguage))
                        .map(seeded -> done));
        if (applied.isErr()) {
            return Result.err(Objects.requireNonNull(applied.error(), "error"));
        }
        return report(
                glossary, projectId, tally, verdicts, suggestions, Objects.requireNonNull(current.data(), "current"));
    }

    private static Result<GlossaryReviewReport> report(
            final GlossaryRepository glossary,
            final String projectId,
            final Tally tally,
            final List<Verdict> verdicts,
            final List<Suggestion> suggestions,
            final List<GlossaryEntry> before) {
        final int removed = tally.removed().size();
        final int suggested = tally.suggested().size();
        final int updated = tally.updated().size();
        log.info(
                "Glossary review finished project={} verdicts={} suggestions={} removed={} updated={} suggested={}",
                projectId,
                verdicts.size(),
                suggestions.size(),
                removed,
                updated,
                suggested);
        final Map<String, String> reasons = reasons(verdicts);
        return glossary.all(projectId)
                .map(after -> new GlossaryReviewReport(
                        removed,
                        updated,
                        suggested,
                        after,
                        EntryChanges.between(before, after, GlossaryEntry::id, entry -> reasons.get(entry.id()))));
    }

    // The model's own words for a verdict: the phrase it quoted, which is all a reply gives as a reason.
    private static Map<String, String> reasons(final List<Verdict> verdicts) {
        final Map<String, String> reasons = new HashMap<>();
        for (final Verdict verdict : verdicts) {
            if (!verdict.evidence().isBlank()) {
                reasons.putIfAbsent(verdict.entry().id(), verdict.evidence());
            }
        }
        return reasons;
    }

    /** The verdicts, then the suggestions, each against the entry as the one before left it; stops at a failure. */
    private static Result<Boolean> applyAll(
            final GlossaryRepository glossary,
            final Map<String, GlossaryEntry> byId,
            final List<Verdict> verdicts,
            final List<Suggestion> suggestions,
            final Tally tally) {
        for (final Verdict verdict : verdicts) {
            final Result<Boolean> applied = applyVerdict(glossary, byId, verdict, tally);
            if (applied.isErr()) {
                return applied;
            }
        }
        for (final Suggestion suggestion : suggestions) {
            final Result<Boolean> applied = applySuggestion(glossary, byId, suggestion, tally);
            if (applied.isErr()) {
                return applied;
            }
        }
        return Result.ok(true);
    }

    private static Result<Boolean> applyVerdict(
            final GlossaryRepository glossary,
            final Map<String, GlossaryEntry> byId,
            final Verdict verdict,
            final Tally tally) {
        final GlossaryEntry now = byId.get(verdict.entry().id());
        if (now == null || !TermReview.isOpen(now)) {
            log.debug(
                    "Glossary review left entry {} alone: gone, locked or given a target meanwhile",
                    verdict.entry().id());
            return Result.ok(false);
        }
        if (removes(now, verdict)) {
            log.debug("Glossary review removes entry {}: not a name", now.id());
            log.trace("Glossary review removes {}", now.term());
            byId.remove(now.id());
            return glossary.remove(now.projectId(), now.id())
                    .map(gone -> gone && tally.removed().add(now.id()));
        }
        final GlossaryEntry guessed = guessed(now, verdict);
        if (guessed.equals(now)) {
            log.debug("Glossary review kept entry {} as it is: verdict {}", now.id(), verdict.kind());
            return Result.ok(false);
        }
        log.debug("Glossary review sets entry {} type={} gender={}", now.id(), guessed.type(), guessed.gender());
        return store(glossary, byId, guessed).map(stored -> tally.updated().add(now.id()));
    }

    private static Result<Boolean> applySuggestion(
            final GlossaryRepository glossary,
            final Map<String, GlossaryEntry> byId,
            final Suggestion suggestion,
            final Tally tally) {
        final GlossaryEntry now = byId.get(suggestion.entry().id());
        if (now == null || !TermReview.isOpen(now)) {
            log.debug(
                    "Glossary suggestion for entry {} dropped: gone, locked or given the person's target meanwhile",
                    suggestion.entry().id());
            return Result.ok(false);
        }
        final GlossaryEntry next = suggestion.onto(now);
        if (next.equals(now)) {
            log.debug("Glossary suggestion for entry {} changes nothing", now.id());
            return Result.ok(false);
        }
        final boolean newTarget = !Objects.equals(next.target(), now.target()) || !now.isSuggested();
        log.debug(
                "Glossary suggestion for entry {} written: newTarget={} gender={}", now.id(), newTarget, next.gender());
        log.trace("Glossary suggestion for {} is {}", now.term(), next.target());
        return store(glossary, byId, next)
                .map(stored -> newTarget
                        ? tally.suggested().add(now.id())
                        : tally.updated().add(now.id()));
    }

    private static Result<GlossaryEntry> store(
            final GlossaryRepository glossary, final Map<String, GlossaryEntry> byId, final GlossaryEntry entry) {
        return glossary.update(entry).map(stored -> {
            byId.put(stored.id(), stored);
            return stored;
        });
    }

    private static boolean removes(final GlossaryEntry now, final Verdict verdict) {
        return verdict.kind() == Kind.NOT_A_NAME && now.type() == TermType.OTHER && now.gender() == Gender.UNKNOWN;
    }

    /**
     * The entry with the verdict's type where its own is unset, and, for a person whose gender is unknown, the
     * verdict's gender: as decided when a cited window shows it, else as a suggestion nobody has verified (15h.A2). A
     * verified gender also replaces a suggested one (a first-name seed, a pre-scan proposal, an earlier unverified
     * review); a gender the person set is kept. A place or a thing takes no gender from the review.
     */
    static GlossaryEntry guessed(final GlossaryEntry now, final Verdict verdict) {
        final GlossaryEntry typed = now.withType(now.type() == TermType.OTHER ? verdict.type() : now.type());
        if (typed.type() != TermType.CHARACTER || verdict.gender() == Gender.UNKNOWN) {
            return typed;
        }
        if (now.gender() == Gender.UNKNOWN) {
            return verdict.genderVerified()
                    ? typed.withInferredGender(verdict.gender())
                    : typed.withSuggestedGender(verdict.gender());
        }
        final boolean replaces = now.isGenderSuggested() && verdict.genderVerified();
        log.debug(
                "Glossary review gender for entry {}: held suggested={} verified={} replaces={}",
                now.id(),
                now.isGenderSuggested(),
                verdict.genderVerified(),
                replaces);
        return replaces ? confirmed(typed, verdict.gender()) : typed;
    }

    // A verified verdict stands as an inferred gender even where it agrees with the suggestion it replaces.
    private static GlossaryEntry confirmed(final GlossaryEntry entry, final Gender gender) {
        return new GlossaryEntry(
                entry.id(),
                entry.projectId(),
                entry.term(),
                entry.target(),
                entry.type(),
                gender,
                entry.locked(),
                entry.origin(),
                false,
                entry.genderSeedTried());
    }
}
