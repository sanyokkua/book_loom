package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.PreScanReplies.Proposal;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The model's name scan, run only when the person asks for it: it finds names a frequency count misses and guesses
 * their type and gender, but it costs time and a provider call. It runs in two stages within one action: the model
 * proposes from the candidates, then the review's verdict step ({@link TermReview}) judges each proposal against every
 * use the book makes of it, and only a name or a term is kept; then each kept entry is given a suggested target
 * ({@link SuggestTargets}). Nothing is written until every call has answered, so a failed call never wipes what the
 * person already has.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class PreScan {

    /** How many candidates one call carries, so a long book stays inside the model's context. */
    static final int BATCH_SIZE = 40;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final GlossaryRepository glossary;
    private final TermReview verdicts;
    private final SuggestTargets suggestions;

    /**
     * Asks the model about the book's candidate names and adds what it proposes to the glossary.
     *
     * @param projectId the project whose glossary receives the entries; never null
     * @param segments the segments whose capitalised words are the candidates; never null, may be empty
     * @param frame the run's language pair and style, for the system message
     * @param policy the Book Brief's name policy, which the suggested targets follow
     * @param calls the seam every model call goes through
     * @return the changes: the entries added, unlocked, each with a suggested target where the model gave one — a term the glossary
     *     holds or the person removed is not among them; or the error of the first failed call or glossary access,
     *     with nothing added by the model's answers
     */
    public Result<EntryChanges<GlossaryEntry>> scan(
            final String projectId,
            final List<Segment> segments,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(calls, "calls");
        try {
            return run(projectId, segments, frame, policy, calls).map(EntryChanges::ofAdded);
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Name scan failed", "The model name scan could not be completed.", null, cause);
            log.error("Unexpected pre-scan failure project={} code={}", projectId, error.code(), cause);
            return Result.err(error);
        }
    }

    private Result<List<GlossaryEntry>> run(
            final String projectId,
            final List<Segment> segments,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        final List<NameCandidate> candidates = FrequencyScan.candidates(segments, 1, frame.sourceLanguage());
        log.info(
                "Model pre-scan started project={} candidates={} batches={}",
                projectId,
                candidates.size(),
                (candidates.size() + BATCH_SIZE - 1) / BATCH_SIZE);
        if (candidates.isEmpty()) {
            return Result.ok(List.of());
        }
        final Result<List<GlossaryEntry>> held = glossary.all(projectId);
        if (held.isErr()) {
            return held;
        }
        final String existing = existingTerms(Objects.requireNonNull(held.data(), "held"));
        final Result<Collection<Proposal>> proposals = ask(candidates, existing, frame, calls);
        if (proposals.isErr()) {
            final AppError failed = Objects.requireNonNull(proposals.error(), "error");
            log.info("Model pre-scan finished project={} ok=false code={}", projectId, failed.code());
            return Result.err(failed);
        }
        final Result<List<GlossaryEntry>> merged =
                merge(projectId, Objects.requireNonNull(proposals.data(), "proposals"), segments, frame, policy, calls);
        log.info("Model pre-scan finished project={} ok={} entriesAdded={}", projectId, merged.isOk(), added(merged));
        return merged;
    }

    /**
     * The proposals the review's verdict step calls a name or a term, as entries: the scan proposes from a capitalised
     * word and its first sentence only, and on the fixture book kept chapter-title words, numbers and language names;
     * the verdict sees every use the book makes of a term. An entry the verdict calls not a name, or leaves without a
     * verdict, is not written.
     */
    private Result<List<GlossaryEntry>> confirmed(
            final List<GlossaryEntry> fresh,
            final List<Segment> segments,
            final CallFrame frame,
            final ModelCalls calls) {
        return verdicts.judge(fresh, segments, frame, calls).map(answered -> {
            final List<GlossaryEntry> kept = answered.stream()
                    .filter(verdict -> verdict.kind() == Kind.NAME || verdict.kind() == Kind.TERM)
                    .map(PreScan::withVerdict)
                    .toList();
            log.debug(
                    "Pre-scan verdicts: {} proposed, {} answered, {} confirmed",
                    fresh.size(),
                    answered.size(),
                    kept.size());
            return kept;
        });
    }

    /**
     * The proposal as the verdict leaves it. The verdict saw windows from across the book and the proposal one
     * sentence, so the verdict's type and gender win (15h.A2); the proposal's gender stays only as an unverified
     * suggestion when the verdict gives none.
     */
    private static GlossaryEntry withVerdict(final Verdict verdict) {
        final GlossaryEntry proposed = verdict.entry();
        final TermType type = verdict.type() == TermType.OTHER ? proposed.type() : verdict.type();
        final GlossaryEntry judged = ReviewCommit.guessed(
                proposed.withType(TermType.OTHER).withInferredGender(Gender.UNKNOWN), verdict.withType(type));
        if (judged.gender() == Gender.UNKNOWN && proposed.gender() != Gender.UNKNOWN) {
            log.debug("Pre-scan entry {} keeps the proposal's gender as a suggestion", proposed.id());
            return judged.withSuggestedGender(proposed.gender());
        }
        return judged;
    }

    private Result<List<GlossaryEntry>> merge(
            final String projectId,
            final Collection<Proposal> proposals,
            final List<Segment> segments,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        return fresh(
                        projectId,
                        proposals,
                        NameHygiene.occurrencesIn(Tokens.visibleTexts(segments)),
                        frame.sourceLanguage())
                .flatMap(fresh -> confirmed(fresh, segments, frame, calls))
                .flatMap(confirmed ->
                        suggestions.suggestOnto(seeded(confirmed, segments, frame), segments, frame, policy, calls))
                .map(suggested -> noteWithoutTarget(suggested, policy))
                .flatMap(this::addAll);
    }

    private static List<GlossaryEntry> seeded(
            final List<GlossaryEntry> confirmed, final List<Segment> segments, final CallFrame frame) {
        return PronounGender.seeded(confirmed, Tokens.visibleTexts(segments), frame.sourceLanguage());
    }

    // A name the policy translates and the suggestion step left without a target is still written, so the person can
    // fill it; the log says which ones, since a book renders them freely until then.
    private static List<GlossaryEntry> noteWithoutTarget(final List<GlossaryEntry> entries, final NamePolicy policy) {
        if (policy != NamePolicy.KEEP_ORIGINAL) {
            entries.stream()
                    .filter(entry -> entry.target() == null || entry.target().isBlank())
                    .forEach(entry -> log.debug("Pre-scan entry {} has no target under policy {}", entry.id(), policy));
        }
        return entries;
    }

    private Result<List<GlossaryEntry>> addAll(final List<GlossaryEntry> entries) {
        final List<GlossaryEntry> added = new ArrayList<>();
        for (final GlossaryEntry entry : entries) {
            final Result<GlossaryEntry> stored = glossary.add(entry);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            added.add(Objects.requireNonNull(stored.data(), "stored"));
        }
        return Result.ok(added);
    }

    private Result<Collection<Proposal>> ask(
            final List<NameCandidate> candidates,
            final String existing,
            final CallFrame frame,
            final ModelCalls calls) {
        final Map<String, Proposal> proposals = new LinkedHashMap<>();
        for (int from = 0, index = 0; from < candidates.size(); from += BATCH_SIZE, index++) {
            final List<NameCandidate> batch = candidates.subList(from, Math.min(candidates.size(), from + BATCH_SIZE));
            final Result<List<Proposal>> answered = runBatch(index, batch, existing, frame, calls);
            if (answered.isErr()) {
                return Result.err(Objects.requireNonNull(answered.error(), "error"));
            }
            for (final Proposal proposal : Objects.requireNonNull(answered.data(), "proposals")) {
                proposals.putIfAbsent(PreScanReplies.key(proposal.candidate().term()), proposal);
            }
        }
        return Result.ok(proposals.values());
    }

    private Result<List<Proposal>> runBatch(
            final int index,
            final List<NameCandidate> batch,
            final String existing,
            final CallFrame frame,
            final ModelCalls calls) {
        final List<ChatMessage> messages = messagesFor(batch, existing, frame);
        final ChatRequest request =
                ChatRequests.build(PromptName.PRESCAN, messages, OutputLimit.forPrescan(batch.size()), false);
        log.trace("Pre-scan batch {} messages {}", index, messages);
        final Result<ChatResponse> reply =
                calls.callAbout(CallKind.PRESCAN, List.of(), request, CallDescriptor.whole(PromptName.PRESCAN));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Pre-scan batch {} of size {} failed code={}", index, batch.size(), error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Pre-scan batch {} reply {}", index, content);
        final Map<String, NameCandidate> byKey = new LinkedHashMap<>();
        batch.forEach(candidate -> byKey.put(PreScanReplies.key(candidate.term()), candidate));
        final List<Proposal> proposals = PreScanReplies.read(mapper, content, byKey);
        log.debug("Pre-scan batch {} answered: size={} proposals={}", index, batch.size(), proposals.size());
        return Result.ok(proposals);
    }

    private List<ChatMessage> messagesFor(
            final List<NameCandidate> batch, final String existing, final CallFrame frame) {
        final String lines = String.join(
                "\n",
                batch.stream()
                        .map(candidate -> candidate.term() + " — " + candidate.firstSentence())
                        .toList());
        final String system = templates.renderSystem(PromptName.PRESCAN, frame).strip();
        final String user = templates
                .renderUser(PromptName.PRESCAN, Map.of("candidates", lines, "existingTerms", existing))
                .strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    /** The proposals as entries, leaving out a term the glossary holds or the person removed. */
    private Result<List<GlossaryEntry>> fresh(
            final String projectId,
            final Collection<Proposal> proposals,
            final ToIntFunction<String> inBook,
            @Nullable final String language) {
        final Result<List<GlossaryEntry>> held = glossary.all(projectId);
        if (held.isErr()) {
            return held;
        }
        final Set<String> heldKeys = new HashSet<>();
        Objects.requireNonNull(held.data(), "held").forEach(entry -> heldKeys.add(PreScanReplies.key(entry.term())));
        final List<GlossaryEntry> fresh = new ArrayList<>();
        final List<String> proposed =
                proposals.stream().map(proposal -> proposal.candidate().term()).toList();
        for (final Proposal proposal : proposals) {
            final String term = proposal.candidate().term();
            if (NameHygiene.rejection(term, proposed, language, inBook).isPresent()) {
                continue;
            }
            final Result<Boolean> removed = glossary.wasRemoved(projectId, term);
            if (removed.isErr()) {
                return Result.err(Objects.requireNonNull(removed.error(), "error"));
            }
            if (!heldKeys.contains(PreScanReplies.key(term)) && !Boolean.TRUE.equals(removed.data())) {
                fresh.add(entryOf(projectId, proposal));
            }
        }
        log.debug(
                "Pre-scan merge project={}: {} proposed, {} neither held nor removed",
                projectId,
                proposals.size(),
                fresh.size());
        return Result.ok(fresh);
    }

    private static GlossaryEntry entryOf(final String projectId, final Proposal proposal) {
        final String term = proposal.candidate().term();
        return new GlossaryEntry(
                GlossaryIds.of(projectId, term), projectId, term, null, proposal.type(), proposal.gender(), false);
    }

    private static String existingTerms(final List<GlossaryEntry> held) {
        return String.join(", ", held.stream().map(GlossaryEntry::term).toList());
    }

    private static int added(final Result<List<GlossaryEntry>> merged) {
        return merged.isOk() ? Objects.requireNonNull(merged.data(), "entries").size() : 0;
    }
}
