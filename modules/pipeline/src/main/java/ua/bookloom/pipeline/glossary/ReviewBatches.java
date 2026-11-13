package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The batched path of the glossary review: the open entries the reading path does not take are sent in batches of
 * numbered windows with the code's pronoun tally, and the model says whether each is a name, its type and gender. An
 * entry whose pronouns decide its gender on their own is decided by the code and not sent.
 */
@Slf4j
final class ReviewBatches {

    private static final int TOKENS_PER_VERDICT = 40;
    private static final int CAP_TOKENS = 2048;

    private final ContextBuilder builder;
    private final ObjectMapper mapper;

    /**
     * Builds its requests on the ANALYSIS frame (design D8) from the catalogue's task templates.
     *
     * @param templates the non-null prompt catalogue
     * @param mapper the non-null object mapper
     */
    ReviewBatches(final PromptTemplates templates, final ObjectMapper mapper) {
        this.builder = new ContextBuilder(Objects.requireNonNull(templates, "templates"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    Result<List<Verdict>> ask(
            final List<GlossaryEntry> open,
            final Map<String, Evidence> evidence,
            final CallFrame frame,
            final ModelCalls calls) {
        final List<Verdict> verdicts = new ArrayList<>();
        final List<GlossaryEntry> asked = new ArrayList<>();
        open.forEach(entry -> decidedByCode(entry, evidence).ifPresentOrElse(verdicts::add, () -> asked.add(entry)));
        final List<List<GlossaryEntry>> batches = batches(asked, evidence, fixedTokens(frame), frame.sourceLanguage());
        log.info(
                "Glossary review asks the model about {} of {} terms in {} batches; {} decided by their pronouns",
                asked.size(),
                open.size(),
                batches.size(),
                verdicts.size());
        for (int index = 0; index < batches.size(); index++) {
            final Result<List<Verdict>> answered = runBatch(index, batches.get(index), evidence, frame, calls);
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
    static Optional<Verdict> decidedByCode(final GlossaryEntry entry, final Map<String, Evidence> evidence) {
        final PronounGender.Evidence pronouns =
                evidence.getOrDefault(entry.term(), Evidence.NONE).pronouns();
        final boolean person = entry.type() == TermType.CHARACTER
                || (entry.type() == TermType.OTHER && entry.genderOrigin() == Origin.LIKELY);
        log.debug(
                "Glossary review code decision for entry {}: type={} genderOrigin={} person={} strong={}",
                entry.id(),
                entry.type(),
                entry.genderOrigin(),
                person,
                pronouns.isStrong());
        final Optional<Gender> decided = person && pronouns.isStrong() ? pronouns.dominant() : Optional.empty();
        decided.ifPresent(gender -> log.debug(
                "Glossary review decides entry {} without the model: {} ({})", entry.id(), gender, pronouns.compact()));
        return decided.map(gender -> Verdict.decided(entry, gender));
    }

    /** The terms cut into batches that each stay within {@link TermReview#INPUT_BUDGET_TOKENS} and {@link TermReview#BATCH_SIZE}. */
    private static List<List<GlossaryEntry>> batches(
            final List<GlossaryEntry> asked,
            final Map<String, Evidence> evidence,
            final int fixedTokens,
            @Nullable final String sourceLanguage) {
        final int room = TermReview.INPUT_BUDGET_TOKENS - fixedTokens;
        final List<List<GlossaryEntry>> batches = new ArrayList<>();
        List<GlossaryEntry> batch = new ArrayList<>();
        int used = 0;
        for (final GlossaryEntry entry : asked) {
            final int size = TokenEstimator.estimate(block(entry, evidence), sourceLanguage);
            if (!batch.isEmpty() && (used + size > room || batch.size() == TermReview.BATCH_SIZE)) {
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
            final CallFrame frame,
            final ModelCalls calls) {
        final ChatRequest request = requestFor(batch, evidence, frame);
        log.debug("Glossary review batch {} asks about {} terms", index, batch.size());
        log.trace("Glossary review batch {} messages {}", index, request.messages());
        final Result<ChatResponse> reply =
                calls.callAbout(CallKind.REVIEW_TERMS, List.of(), request, TermReviewReplies.asking(batch));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn(
                    "Glossary review batch {} of size {} failed code={} reason={}",
                    index,
                    batch.size(),
                    error.code(),
                    FailureReason.tokenIn(error.details()));
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Glossary review batch {} reply {}", index, content);
        final Map<String, GlossaryEntry> byKey = new LinkedHashMap<>();
        batch.forEach(entry -> byKey.put(GlossaryKeys.of(entry.term()), entry));
        final List<Verdict> verdicts = TermReviewReplies.read(mapper, content, byKey, evidence);
        log.debug("Glossary review batch {} answered: size={} verdicts={}", index, batch.size(), verdicts.size());
        calls.answered(TermReviewReplies.shown(batch, verdicts));
        return Result.ok(verdicts);
    }

    private ChatRequest requestFor(
            final List<GlossaryEntry> batch, final Map<String, Evidence> evidence, final CallFrame frame) {
        final String blocks = String.join(
                "\n", batch.stream().map(entry -> block(entry, evidence)).toList());
        return builder.analysisRequest(
                PromptName.REVIEW_TERMS,
                frame,
                Map.of("terms", blocks),
                new OutputLimit(batch.size() * TOKENS_PER_VERDICT, CAP_TOKENS));
    }

    // What every batch sends besides its terms: the frame's sentences and the task, measured once with no term.
    private int fixedTokens(final CallFrame frame) {
        final int tokens = builder
                .analysisRequest(PromptName.REVIEW_TERMS, frame, Map.of("terms", ""), new OutputLimit(1, CAP_TOKENS))
                .messages()
                .stream()
                .mapToInt(message -> TokenEstimator.estimate(message.content(), null))
                .sum();
        log.debug("Glossary review fixed part tokens={}", tokens);
        return tokens;
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
}
