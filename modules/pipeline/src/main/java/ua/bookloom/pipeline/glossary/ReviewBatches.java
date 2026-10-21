package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.RequestDefaults;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The batched path of the glossary review: the open entries the reading path does not take are sent in batches of
 * numbered windows with the code's pronoun tally, and the model says whether each is a name, its type and gender. An
 * entry whose pronouns decide its gender on their own is decided by the code and not sent.
 */
@Slf4j
@RequiredArgsConstructor
final class ReviewBatches {

    private static final int TOKENS_PER_VERDICT = 40;
    private static final int CAP_TOKENS = 2048;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    Result<List<Verdict>> ask(
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
            final Result<List<Verdict>> answered =
                    runBatch(index, batches.get(index), evidence, system, frame.requestDefaults(), calls);
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
                || (entry.type() == TermType.OTHER && entry.genderOrigin() == GenderOrigin.SUGGESTED);
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
            final String system,
            @Nullable final String sourceLanguage) {
        final int room = TermReview.INPUT_BUDGET_TOKENS - TokenEstimator.estimate(system, null);
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
            final String system,
            final RequestDefaults defaults,
            final ModelCalls calls) {
        final List<ChatMessage> messages = messagesFor(batch, evidence, system);
        final ChatRequest request = ChatRequests.build(
                PromptName.REVIEW_TERMS,
                messages,
                new OutputLimit(batch.size() * TOKENS_PER_VERDICT, CAP_TOKENS),
                false,
                defaults);
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
}
