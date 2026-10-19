package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The model's review of the glossary, run only when the person asks, one path for Review and the names pass (15i.N5):
 * the unlocked terms with no target that the quality dial's {@link NamesPlan} reads are read in context, one term per
 * call, and decided by the code from the readings ({@link NameReadings}); every other such term is sent in a batch with
 * how often the book uses it, the pronouns the code read after it and numbered windows from across the book
 * ({@link NameWindows}), and the model says whether it is a name, a term or not a name, its type and gender, and which
 * windows it relied on. A batched name whose pronouns decide its gender on their own
 * ({@link PronounGender.Evidence#isStrong()}) is decided by the code and not sent. Nothing is changed until every call
 * has answered, so a failed or cancelled call leaves the glossary as it was.
 *
 * <p>Once every verdict is in, the terms that remain are given a suggested target in calls of their own
 * ({@link SuggestTargets}), and verdicts and suggestions are written together.
 *
 * <p>The person's work is never undone: a locked term or one with the person's target is not sent, and at commit time an entry is
 * read again, so an edit made while the model was thinking wins. A term judged not a name is removed only when its
 * type and gender were never set — a set type means a person or an earlier scan already judged it — and a type or
 * gender takes the model's guess only while it is still {@code OTHER}/{@code UNKNOWN}.
 */
@Slf4j
public final class TermReview {

    /** The most terms one call carries, whatever their size: the schema's cap on the verdicts of one reply. */
    static final int BATCH_SIZE = 40;

    /** The input tokens one call may take, system message included: a batch is cut by size, not by a term count. */
    static final int INPUT_BUDGET_TOKENS = 5000;

    private final GlossaryRepository glossary;
    private final SuggestTargets suggestions;
    private final NameReadings readings;
    private final ReviewBatches batched;

    /**
     * Builds the review; the reading path is built from the same templates and mapper.
     *
     * @param templates the prompt templates; never null
     * @param mapper the mapper replies are read with; never null
     * @param glossary where the entries are read and the verdicts written; never null
     * @param suggestions the target suggestions asked once the verdicts are in; never null
     */
    @Inject
    public TermReview(
            final PromptTemplates templates,
            final ObjectMapper mapper,
            final GlossaryRepository glossary,
            final SuggestTargets suggestions) {
        Objects.requireNonNull(templates, "templates");
        Objects.requireNonNull(mapper, "mapper");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.suggestions = Objects.requireNonNull(suggestions, "suggestions");
        this.readings = new NameReadings(templates, mapper);
        this.batched = new ReviewBatches(templates, mapper);
    }

    /**
     * Reviews the project's glossary with the model at a quality dial: the entries the dial's {@link NamesPlan} reads
     * are read in context and decided by the code, the rest go to the batched review.
     *
     * @param projectId the project whose glossary is reviewed; never null
     * @param story the book's body segments, where the evidence is read; never null
     * @param frame the run's language pair and style, for the system message; never null
     * @param policy the Book Brief's name policy, which the suggested targets follow; never null
     * @param dial the project's quality dial, which picks the entries read; never null
     * @param calls the seam every model call goes through; never null
     * @return what was removed, updated and suggested and the glossary after; or the first failed call's or glossary
     *     access's error, with nothing changed by the model's answers
     */
    public Result<GlossaryReviewReport> review(
            final String projectId,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final QualityDial dial,
            final ModelCalls calls) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(calls, "calls");
        try {
            return glossary.all(projectId)
                    .flatMap(held -> run(projectId, held, new Inputs(story, frame, policy, dial, calls)));
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Glossary review failed",
                    "The glossary review could not be completed.",
                    null,
                    cause);
            log.error("Unexpected glossary review failure project={} code={}", projectId, error.code(), cause);
            return Result.err(error);
        }
    }

    /**
     * What the verdicts left and what the reading calls read of each entry they read.
     *
     * @param report what the verdicts removed and rewrote, with no target suggested
     * @param readings what the calls read of each entry read, by entry id; an entry the plan did not read has none
     * @param verified the ids the readings decided are proper names, the same set a review hands its suggestions (15i.T4)
     */
    record Reviewed(GlossaryReviewReport report, Map<String, Reading> readings, Set<String> verified) {}

    /**
     * The verdicts of {@link #review} without its suggestion step (15i.N8): the names pass reads the recurring terms
     * before it asks for any rendering, so it commits the verdicts now and suggests later with the readings returned
     * here. Nothing is written until every call has answered.
     *
     * @param projectId the project whose glossary is decided; never null
     * @param story the book's body segments, where the evidence is read; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param dial the project's quality dial, which picks the entries read; never null
     * @param calls the seam every model call goes through; never null
     * @return the report of the committed verdicts and the readings; or the first failed call's or glossary access's
     *     error, with nothing changed by the model's answers
     */
    Result<Reviewed> decide(
            final String projectId,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final QualityDial dial,
            final ModelCalls calls) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Glossary decide project={} policy={} dial={}", projectId, policy, dial);
        final Inputs in = new Inputs(story, frame, policy, dial, calls);
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> open =
                    held.stream().filter(TermReview::isOpen).toList();
            log.info(
                    "Glossary decide started project={} held={} open={} dial={}",
                    projectId,
                    held.size(),
                    open.size(),
                    dial);
            if (open.isEmpty()) {
                return Result.ok(new Reviewed(new GlossaryReviewReport(0, 0, held), Map.of(), Set.of()));
            }
            return verdictsOn(open, held, in)
                    .flatMap(decided -> commit(projectId, decided.verdicts(), List.of(), in)
                            .map(report -> new Reviewed(report, decided.readings(), decided.verified())));
        });
    }

    /** The verdicts, what the readings read, and the ids the readings decided are names (15i.T4). */
    private record Decided(List<Verdict> verdicts, Map<String, Reading> readings, Set<String> verified) {}

    /** What every call of one review is asked with. */
    private record Inputs(BookSlice story, CallFrame frame, NamePolicy policy, QualityDial dial, ModelCalls calls) {}

    private Result<GlossaryReviewReport> run(final String projectId, final List<GlossaryEntry> held, final Inputs in) {
        final List<GlossaryEntry> open =
                held.stream().filter(TermReview::isOpen).toList();
        log.info(
                "Glossary review started project={} held={} reviewed={} policy={} dial={}",
                projectId,
                held.size(),
                open.size(),
                in.policy(),
                in.dial());
        if (open.isEmpty()) {
            return Result.ok(new GlossaryReviewReport(0, 0, held));
        }
        final Result<Decided> verdicts = verdictsOn(open, held, in);
        if (verdicts.isErr()) {
            log.warn("Glossary review project={} changed nothing: a call failed", projectId);
            return Result.err(Objects.requireNonNull(verdicts.error(), "error"));
        }
        final Decided decided = Objects.requireNonNull(verdicts.data(), "verdicts");
        final Result<List<Suggestion>> suggested = suggest(open, held, decided, in);
        if (suggested.isErr()) {
            log.warn("Glossary review project={} changed nothing: a suggestion call failed", projectId);
            return Result.err(Objects.requireNonNull(suggested.error(), "error"));
        }
        return commit(projectId, decided.verdicts(), Objects.requireNonNull(suggested.data(), "suggestions"), in);
    }

    private Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> open, final List<GlossaryEntry> held, final Decided decided, final Inputs in) {
        return suggestions.suggest(
                ReviewCommit.survivors(open, decided.verdicts()),
                in.story(),
                in.frame(),
                in.policy(),
                in.calls(),
                new TargetContext(held, decided.readings(), decided.verified()));
    }

    // A strong pronoun case is decided by the code with no call; of the rest, the entries the plan reads are decided
    // from their readings and the others by the batch. All three give verdicts of one shape.
    private Result<Decided> verdictsOn(
            final List<GlossaryEntry> open, final List<GlossaryEntry> held, final Inputs in) {
        final Map<String, Evidence> evidence = evidenceOf(open, held, in);
        final List<Verdict> all = new ArrayList<>();
        final List<GlossaryEntry> rest = new ArrayList<>();
        open.forEach(
                entry -> ReviewBatches.decidedByCode(entry, evidence).ifPresentOrElse(all::add, () -> rest.add(entry)));
        log.debug("Glossary review: {} of {} open entries decided by their pronouns", all.size(), open.size());
        final Set<String> byCode = idsOf(all);
        return readings.read(
                        new NameReadings.Inputs(rest, held, evidence, in.story(), in.frame(), in.dial(), in.calls()))
                .flatMap(outcome -> batched.ask(outcome.unread(), evidence, in.frame(), in.calls())
                        .map(batched -> {
                            all.addAll(outcome.verdicts());
                            all.addAll(batched);
                            return new Decided(all, outcome.readings(), verifiedNames(byCode, outcome));
                        }));
    }

    // The one definition of a verified name, for a review and for the names pass alike: a person the pronouns decided
    // without a call (a verified verdict), or a name the readings decided.
    private static Set<String> verifiedNames(final Set<String> byCode, final NameReadings.Outcome outcome) {
        final Set<String> verified = new HashSet<>(byCode);
        verified.addAll(outcome.names());
        return verified;
    }

    private static Set<String> idsOf(final List<Verdict> verdicts) {
        return verdicts.stream().map(verdict -> verdict.entry().id()).collect(Collectors.toSet());
    }

    // The pronouns after any spelling of a person count for each of them, so the evidence is read over the clusters of
    // the whole glossary, not only of the open entries (15i.N2).
    private static Map<String, Evidence> evidenceOf(
            final List<GlossaryEntry> open, final List<GlossaryEntry> held, final Inputs in) {
        final String language = in.frame().analysisLanguage();
        return NameWindows.of(
                in.story().index(),
                in.story().ordinals(),
                open,
                NameClusters.of(held, in.story().index(), language),
                language);
    }

    private Result<GlossaryReviewReport> commit(
            final String projectId, final List<Verdict> verdicts, final List<Suggestion> suggestions, final Inputs in) {
        return ReviewCommit.apply(
                glossary,
                projectId,
                verdicts,
                suggestions,
                in.frame().analysisLanguage(),
                in.story().visibleTexts());
    }

    /**
     * The model's verdict on entries the glossary does not hold yet — the model scan's proposals — with the same
     * prompt, evidence and batching as a review of held entries, and nothing written.
     *
     * @param entries the entries to judge; never null
     * @param story the book's body segments, where the evidence is read; never null
     * @param frame the run's language pair and style; never null
     * @param calls the seam every model call goes through; never null
     * @return the verdicts the replies hold, which may leave an entry without one; or the first failed call's error
     */
    Result<List<Verdict>> judge(
            final List<GlossaryEntry> entries, final BookSlice story, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(entries, "entries");
        log.debug("Judging {} proposed entries", entries.size());
        if (entries.isEmpty()) {
            return Result.ok(List.of());
        }
        final Map<String, Evidence> evidence =
                NameWindows.of(story.index(), story.ordinals(), entries, frame.analysisLanguage());
        return batched.ask(entries, evidence, frame, calls);
    }

    /**
     * Whether the model may still change an entry: it is unlocked and its target is empty or only an earlier
     * suggestion, so a re-run refreshes suggestions but never touches the person's choice.
     */
    static boolean isOpen(final GlossaryEntry entry) {
        return !entry.locked()
                && (entry.isSuggested()
                        || Optional.ofNullable(entry.target())
                                .map(String::isBlank)
                                .orElse(true));
    }
}
