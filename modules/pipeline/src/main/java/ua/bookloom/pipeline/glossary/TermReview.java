package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The model's review of the glossary, run only when the person asks: each unlocked term with no target is sent with
 * how often the book uses it, the pronouns the code read after it and numbered windows from across the book
 * ({@link NameWindows}), and the model says whether it is a name, a term or not a name, its type and gender, and which
 * windows it relied on. A name whose pronouns decide its gender on their own ({@link PronounGender.Evidence#isStrong()})
 * is decided by the code and not sent. Nothing is changed until every batch has answered, so a failed or cancelled call
 * leaves the glossary as it was.
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
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TermReview {

    /** The most terms one call carries, whatever their size: the schema's cap on the verdicts of one reply. */
    static final int BATCH_SIZE = 40;

    /** The input tokens one call may take, system message included: a batch is cut by size, not by a term count. */
    static final int INPUT_BUDGET_TOKENS = 5000;

    private static final int TOKENS_PER_VERDICT = 40;
    private static final int CAP_TOKENS = 2048;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final GlossaryRepository glossary;
    private final SuggestTargets suggestions;

    /**
     * Reviews the project's glossary with the model.
     *
     * @param projectId the project whose glossary is reviewed; never null
     * @param segments the book's body segments, where the evidence is read; never null
     * @param frame the run's language pair and style, for the system message; never null
     * @param policy the Book Brief's name policy, which the suggested targets follow; never null
     * @param calls the seam every model call goes through; never null
     * @return what was removed, updated and suggested and the glossary after; or the first failed call's or glossary
     *     access's error, with nothing changed by the model's answers
     */
    public Result<GlossaryReviewReport> review(
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
            return glossary.all(projectId)
                    .flatMap(held -> run(projectId, held, new Inputs(segments, frame, policy, calls)));
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

    /** What every call of one review is asked with. */
    private record Inputs(List<Segment> segments, CallFrame frame, NamePolicy policy, ModelCalls calls) {}

    private Result<GlossaryReviewReport> run(final String projectId, final List<GlossaryEntry> held, final Inputs in) {
        final List<GlossaryEntry> open =
                held.stream().filter(TermReview::isOpen).toList();
        log.info(
                "Glossary review started project={} held={} reviewed={} policy={}",
                projectId,
                held.size(),
                open.size(),
                in.policy());
        if (open.isEmpty()) {
            return Result.ok(new GlossaryReviewReport(0, 0, held));
        }
        final Map<String, Evidence> evidence =
                NameWindows.of(in.segments(), open, in.frame().sourceLanguage());
        final Result<List<Verdict>> verdicts = ask(open, evidence, in.frame(), in.calls());
        if (verdicts.isErr()) {
            log.warn("Glossary review project={} changed nothing: a call failed", projectId);
            return Result.err(Objects.requireNonNull(verdicts.error(), "error"));
        }
        final List<Verdict> answered = Objects.requireNonNull(verdicts.data(), "verdicts");
        final Result<List<Suggestion>> suggested = suggestions.suggest(
                ReviewCommit.survivors(open, answered), in.segments(), in.frame(), in.policy(), in.calls());
        if (suggested.isErr()) {
            log.warn("Glossary review project={} changed nothing: a suggestion call failed", projectId);
            return Result.err(Objects.requireNonNull(suggested.error(), "error"));
        }
        return commit(projectId, answered, Objects.requireNonNull(suggested.data(), "suggestions"), in);
    }

    private Result<GlossaryReviewReport> commit(
            final String projectId, final List<Verdict> verdicts, final List<Suggestion> suggestions, final Inputs in) {
        return ReviewCommit.apply(
                glossary,
                projectId,
                verdicts,
                suggestions,
                in.frame().sourceLanguage(),
                Tokens.visibleTexts(in.segments()));
    }

    /**
     * The model's verdict on entries the glossary does not hold yet — the model scan's proposals — with the same
     * prompt, evidence and batching as a review of held entries, and nothing written.
     *
     * @param entries the entries to judge; never null
     * @param segments the book's body segments, where the evidence is read; never null
     * @param frame the run's language pair and style; never null
     * @param calls the seam every model call goes through; never null
     * @return the verdicts the replies hold, which may leave an entry without one; or the first failed call's error
     */
    Result<List<Verdict>> judge(
            final List<GlossaryEntry> entries,
            final List<Segment> segments,
            final CallFrame frame,
            final ModelCalls calls) {
        Objects.requireNonNull(entries, "entries");
        log.debug("Judging {} proposed entries", entries.size());
        if (entries.isEmpty()) {
            return Result.ok(List.of());
        }
        final Map<String, Evidence> evidence = NameWindows.of(segments, entries, frame.sourceLanguage());
        return ask(entries, evidence, frame, calls);
    }

    private Result<List<Verdict>> ask(
            final List<GlossaryEntry> open,
            final Map<String, Evidence> evidence,
            final CallFrame frame,
            final ModelCalls calls) {
        final List<Verdict> verdicts = new ArrayList<>();
        final List<GlossaryEntry> asked = new ArrayList<>();
        open.forEach(entry -> decidedByCode(entry, evidence).ifPresentOrElse(verdicts::add, () -> asked.add(entry)));
        final String system =
                templates.renderSystem(PromptName.REVIEW_TERMS, frame).strip();
        final List<List<GlossaryEntry>> batches = batches(asked, evidence, system, frame.sourceLanguage());
        log.info(
                "Glossary review asks the model about {} of {} terms in {} batches; {} decided by their pronouns",
                asked.size(),
                open.size(),
                batches.size(),
                verdicts.size());
        for (int index = 0; index < batches.size(); index++) {
            final Result<List<Verdict>> answered = runBatch(index, batches.get(index), evidence, system, calls);
            if (answered.isErr()) {
                return answered;
            }
            verdicts.addAll(Objects.requireNonNull(answered.data(), "verdicts"));
        }
        return Result.ok(verdicts);
    }

    // A strong pronoun case decides a person's gender alone: a character, or an untyped entry a first-name seed already
    // marked as a person. A term, a place, a title and any other untyped entry go to the model, since "she" after a
    // ship's or a company's name does not make it a character.
    private static Optional<Verdict> decidedByCode(final GlossaryEntry entry, final Map<String, Evidence> evidence) {
        final PronounGender.Evidence pronouns =
                evidence.getOrDefault(entry.term(), Evidence.NONE).pronouns();
        final boolean person =
                entry.type() == TermType.CHARACTER || (entry.type() == TermType.OTHER && entry.isGenderSuggested());
        log.debug(
                "Glossary review code decision for entry {}: type={} person={} strong={}",
                entry.id(),
                entry.type(),
                person,
                pronouns.isStrong());
        final Optional<Gender> decided = person && pronouns.isStrong() ? pronouns.dominant() : Optional.empty();
        decided.ifPresent(gender -> log.debug(
                "Glossary review decides entry {} without the model: {} ({})", entry.id(), gender, pronouns.compact()));
        return decided.map(gender -> Verdict.decided(entry, gender));
    }

    /** The terms cut into batches that each stay within {@link #INPUT_BUDGET_TOKENS} and {@link #BATCH_SIZE}. */
    private static List<List<GlossaryEntry>> batches(
            final List<GlossaryEntry> asked,
            final Map<String, Evidence> evidence,
            final String system,
            @Nullable final String sourceLanguage) {
        final int room = INPUT_BUDGET_TOKENS - TokenEstimator.estimate(system, null);
        final List<List<GlossaryEntry>> batches = new ArrayList<>();
        List<GlossaryEntry> batch = new ArrayList<>();
        int used = 0;
        for (final GlossaryEntry entry : asked) {
            final int size = TokenEstimator.estimate(block(entry, evidence), sourceLanguage);
            if (!batch.isEmpty() && (used + size > room || batch.size() == BATCH_SIZE)) {
                batches.add(batch);
                batch = new ArrayList<>();
                used = 0;
            }
            batch.add(entry);
            used += size;
        }
        if (!batch.isEmpty()) {
            batches.add(batch);
        }
        return batches;
    }

    private Result<List<Verdict>> runBatch(
            final int index,
            final List<GlossaryEntry> batch,
            final Map<String, Evidence> evidence,
            final String system,
            final ModelCalls calls) {
        final List<ChatMessage> messages = messagesFor(batch, evidence, system);
        final ChatRequest request = ChatRequests.build(
                PromptName.REVIEW_TERMS,
                messages,
                new OutputLimit(batch.size() * TOKENS_PER_VERDICT, CAP_TOKENS),
                false);
        log.debug("Glossary review batch {} asks about {} terms", index, batch.size());
        log.trace("Glossary review batch {} messages {}", index, messages);
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.REVIEW_TERMS, List.of(), request, CallDescriptor.whole(PromptName.REVIEW_TERMS));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Glossary review batch {} of size {} failed code={}", index, batch.size(), error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Glossary review batch {} reply {}", index, content);
        final Map<String, GlossaryEntry> byKey = new LinkedHashMap<>();
        batch.forEach(entry -> byKey.put(GlossaryKeys.of(entry.term()), entry));
        final List<Verdict> verdicts = TermReviewReplies.read(mapper, content, byKey, evidence);
        log.debug("Glossary review batch {} answered: size={} verdicts={}", index, batch.size(), verdicts.size());
        return Result.ok(verdicts);
    }

    private List<ChatMessage> messagesFor(
            final List<GlossaryEntry> batch, final Map<String, Evidence> evidence, final String system) {
        final String blocks = String.join(
                "\n", batch.stream().map(entry -> block(entry, evidence)).toList());
        final String user = templates
                .renderUser(PromptName.REVIEW_TERMS, Map.of("terms", blocks))
                .strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    // One term: its count and the code's pronoun tally on the line, then its numbered windows.
    private static String block(final GlossaryEntry entry, final Map<String, Evidence> evidence) {
        final Evidence used = evidence.getOrDefault(entry.term(), Evidence.NONE);
        final String tally = used.pronouns().compact();
        final StringBuilder block = new StringBuilder()
                .append("- ")
                .append(entry.term())
                .append(" — ")
                .append(used.count())
                .append("× — pronouns: ")
                .append(tally.isEmpty() ? "none" : tally);
        for (int number = 1; number <= used.windows().size(); number++) {
            block.append("\n  [")
                    .append(number)
                    .append("] ")
                    .append(used.windows().get(number - 1).text());
        }
        return block.toString();
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
