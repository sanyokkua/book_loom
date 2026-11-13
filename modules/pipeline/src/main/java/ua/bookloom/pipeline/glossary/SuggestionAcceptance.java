package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.LockRule.Decision;
import ua.bookloom.pipeline.glossary.TargetValidator.Verdict;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Accept all as a validated action (design D3, D4): every suggested target is cleaned of stress marks and checked
 * against the target language's rules; one that passes with no review flag is accepted, pinned or locked by
 * {@link LockRule}, and the rest stay suggested with their reasons. Only a suggested entry with a target is read: an
 * entry with none, a pinned one and a locked one are never touched. Everything is written in one step, and a target
 * the cleaning changed is handed to {@code targetChanged}, which defers the decided segments that used the old one.
 */
@Slf4j
@RequiredArgsConstructor
final class SuggestionAcceptance {

    private final GlossaryRepository glossary;
    private final BiFunction<GlossaryEntry, GlossaryEntry, Result<GlossaryEntry>> targetChanged;

    /** One decided suggestion: the entry as it is to be stored, and the verdict, null for an accepted one. */
    private record Decided(
            GlossaryEntry before,
            GlossaryEntry after,
            @Nullable Verdict rejected) {}

    /**
     * Accepts the project's valid suggestions.
     *
     * @param projectId the project
     * @param brief the project's brief, whose target language, name policy and conventions decide
     * @param targetLanguage the brief's target language
     * @param analysisLanguage the language the book is read in, or null for the generic rules
     * @return the accepted and the rejected entries, or the store's error
     */
    Result<AcceptReport> accept(
            final String projectId,
            final BookBrief brief,
            final String targetLanguage,
            @Nullable final String analysisLanguage) {
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> suggested = held.stream()
                    .filter(SuggestionAcceptance::isSuggestedWithTarget)
                    .toList();
            log.debug("Accept suggested project={} held={} suggested={}", projectId, held.size(), suggested.size());
            final TargetContext context = new TargetContext(held, Map.of());
            final TargetValidator validator =
                    TargetValidator.of(targetLanguage, analysisLanguage, brief.names(), context, brief.conventions());
            final boolean inflects = LanguageRules.bundled().namesInflect(targetLanguage);
            final List<Decided> decided = suggested.stream()
                    .map(entry -> decide(entry, targetLanguage, validator, brief, context, inflects))
                    .toList();
            return store(projectId, decided);
        });
    }

    private static boolean isSuggestedWithTarget(final GlossaryEntry entry) {
        return entry.isSuggested() && entry.target() != null && !entry.target().isBlank();
    }

    private static Decided decide(
            final GlossaryEntry entry,
            final String targetLanguage,
            final TargetValidator validator,
            final BookBrief brief,
            final TargetContext context,
            final boolean inflects) {
        final String held = Objects.requireNonNull(entry.target(), "target");
        final String clean = Objects.requireNonNull(TargetText.clean(held, targetLanguage), "clean");
        if (clean.isBlank()) {
            log.debug("Accept suggested entry {} left: nothing but marks", entry.id());
            return new Decided(entry, entry, new Verdict(List.of(), List.of()));
        }
        final GlossaryEntry cleaned = clean.equals(held) ? entry : entry.withSuggestedTarget(clean);
        final Verdict verdict = validator.validate(cleaned, clean);
        if (!verdict.passes()) {
            return new Decided(entry, cleaned, verdict);
        }
        final LockRule.Basis basis = SuggestionLocks.basisOf(cleaned, brief.names(), context);
        final Decision decision = LockRule.decide(entry.term(), clean, basis, inflects);
        log.debug("Accept suggested entry {} decided {}", entry.id(), decision);
        // Accept all is the person's act, so the target is the person's; a lock by the rule confirms no gender.
        final GlossaryEntry accepted =
                switch (decision) {
                    case PINNED -> cleaned.accepted();
                    case LOCKED -> cleaned.withRuleLock().accepted();
                };
        return new Decided(entry, accepted, null);
    }

    private Result<AcceptReport> store(final String projectId, final List<Decided> decided) {
        return glossary.putAll(
                        projectId,
                        decided.stream()
                                .filter(each ->
                                        each.rejected() == null || !each.after().equals(each.before()))
                                .map(Decided::after)
                                .toList())
                .flatMap(stored -> recordChangedTargets(decided))
                .map(recorded -> report(projectId, decided));
    }

    private Result<Integer> recordChangedTargets(final List<Decided> decided) {
        int changed = 0;
        for (final Decided each : decided) {
            if (!Objects.equals(each.before().target(), each.after().target())) {
                final Result<GlossaryEntry> recorded = targetChanged.apply(each.before(), each.after());
                if (recorded.isErr()) {
                    return Result.err(Objects.requireNonNull(recorded.error(), "error"));
                }
                changed++;
            }
        }
        log.debug("Accept suggested targets cleaned of stress marks={}", changed);
        return Result.ok(changed);
    }

    private static AcceptReport report(final String projectId, final List<Decided> decided) {
        final List<GlossaryEntry> accepted = new ArrayList<>();
        final List<AcceptReport.Rejected> rejected = new ArrayList<>();
        final Map<TargetValidator.Reason, Integer> refusals = new EnumMap<>(TargetValidator.Reason.class);
        final Map<TargetValidator.Flag, Integer> flags = new EnumMap<>(TargetValidator.Flag.class);
        for (final Decided each : decided) {
            final Verdict verdict = each.rejected();
            if (verdict == null) {
                accepted.add(each.after());
                continue;
            }
            rejected.add(new AcceptReport.Rejected(each.after(), issuesOf(verdict)));
            verdict.rejections().forEach(reason -> refusals.merge(reason, 1, Integer::sum));
            verdict.flags().forEach(flag -> flags.merge(flag, 1, Integer::sum));
            log.trace(
                    "Accept suggested left '{}' -> '{}': {}",
                    each.after().term(),
                    each.after().target(),
                    issuesOf(verdict));
        }
        log.info("Accept suggested project={} accepted={} rejected={}", projectId, accepted.size(), rejected.size());
        log.debug("Accept suggested project={} refusals={} flags={}", projectId, refusals, flags);
        return new AcceptReport(accepted, rejected);
    }

    // A target that is nothing but marks is left blank by the cleaning: it is no target to accept, for its marks.
    private static List<TargetIssue> issuesOf(final Verdict verdict) {
        final List<TargetIssue> issues = TargetIssues.of(verdict);
        return issues.isEmpty() ? List.of(TargetIssue.STRESS_MARK) : issues;
    }
}
