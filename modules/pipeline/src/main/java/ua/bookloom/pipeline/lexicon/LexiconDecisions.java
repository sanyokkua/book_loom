package ua.bookloom.pipeline.lexicon;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.glossary.LexiconReading.Decision;

/**
 * Applies what the code decided from the readings of the recurring terms (15i.N7): a kept term enters or keeps the
 * rendering read, written as the model's so a person's never yields to it; a dropped one goes; a named thing moves to
 * the glossary with its rendering as a suggestion; an undecided one is left as it is.
 */
@Slf4j
@RequiredArgsConstructor
final class LexiconDecisions {

    private final LexiconRepository lexicon;
    private final GlossaryRepository glossary;

    // A kept term enters with the rendering the reading gave, marked as the model's; a named thing goes to the
    // glossary; a dropped or undecided one is not added.
    Result<Integer> addDecided(
            final String projectId, final List<String> candidates, final Map<String, Decision> decisions) {
        log.debug("Lexicon model scan project={} deciding {} candidates", projectId, candidates.size());
        int added = 0;
        for (final String term : candidates) {
            final Decision decision = decisions.getOrDefault(LexiconEntry.keyOf(term), Decision.undecided());
            log.debug("Lexicon model scan project={} verdict={}", projectId, decision.verdict());
            final Result<Boolean> stored =
                    switch (decision.verdict()) {
                        case KEEP ->
                            lexicon.put(LexiconEntry.of(projectId, term).withModelRendering(decision.rendering()))
                                    .map(entry -> true);
                        case PROMOTE ->
                            addToGlossary(projectId, term, decision.rendering(), true)
                                    .map(entry -> false);
                        case DROP, UNDECIDED -> Result.ok(false);
                    };
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            added += Boolean.TRUE.equals(stored.data()) ? 1 : 0;
        }
        return Result.ok(added);
    }

    // Only a term the reading explicitly dropped goes; one it left undecided stays.
    Result<List<LexiconEntry>> applyReview(
            final String projectId, final List<LexiconEntry> asked, final Map<String, Decision> decisions) {
        log.debug("Lexicon review project={} applying decisions to {} terms", projectId, asked.size());
        for (final LexiconEntry entry : asked) {
            final Decision decision = decisions.getOrDefault(LexiconEntry.keyOf(entry.term()), Decision.undecided());
            log.debug("Lexicon review project={} verdict={}", projectId, decision.verdict());
            final Result<Boolean> applied =
                    switch (decision.verdict()) {
                        case DROP -> lexicon.remove(projectId, entry.term());
                        case KEEP -> keepWith(projectId, entry, decision.rendering());
                        case PROMOTE ->
                            promote(projectId, entry.withModelRendering(decision.rendering()))
                                    .map(promoted -> true);
                        case UNDECIDED -> Result.ok(false);
                    };
            if (applied.isErr()) {
                return Result.err(Objects.requireNonNull(applied.error(), "error"));
            }
        }
        return lexicon.all(projectId);
    }

    private Result<Boolean> keepWith(
            final String projectId, final LexiconEntry entry, @Nullable final String rendering) {
        if (rendering == null) {
            return Result.ok(false);
        }
        return lexicon.update(projectId, entry.term(), found -> found.withModelRendering(rendering))
                .map(Optional::isPresent);
    }

    Result<GlossaryEntry> promote(final String projectId, final LexiconEntry entry) {
        log.debug("Lexicon promote project={}", projectId);
        final String rendering = entry.established().orElse(null);
        // A rendering a reading wrote stays the model's when its term moves: a suggestion the person may replace.
        final boolean isModels = entry.chosen() != null && !entry.hasPersonsRendering();
        final Result<GlossaryEntry> added = addToGlossary(projectId, entry.term(), rendering, isModels);
        if (added.isErr()) {
            return added;
        }
        final Result<Boolean> removed = lexicon.remove(projectId, entry.term());
        log.info("Lexicon term promoted project={} hadRendering={}", projectId, rendering != null);
        return removed.isErr() ? Result.err(Objects.requireNonNull(removed.error(), "error")) : added;
    }

    // The glossary holds a named thing as a term; the rendering is a suggestion unless a person wrote it. A term the
    // glossary already holds stays as it is there: moving it only takes it out of the lexicon (15i review).
    private Result<GlossaryEntry> addToGlossary(
            final String projectId, final String term, @Nullable final String rendering, final boolean isModels) {
        final Result<Optional<GlossaryEntry>> held = glossary.findByTerm(projectId, term);
        if (held.isErr()) {
            return Result.err(Objects.requireNonNull(held.error(), "error"));
        }
        final Optional<GlossaryEntry> found = Objects.requireNonNull(held.data(), "held");
        if (found.isPresent()) {
            log.debug("Lexicon promote project={}: the glossary already holds the term, nothing added", projectId);
            return Result.ok(found.get());
        }
        return glossary.add(new GlossaryEntry(
                GlossaryIds.of(projectId, term),
                projectId,
                term,
                rendering,
                TermType.TERM,
                Gender.UNKNOWN,
                false,
                isModels ? TargetOrigin.SUGGESTED : TargetOrigin.PERSON));
    }
}
