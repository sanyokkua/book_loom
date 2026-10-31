package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The name-knowledge stage's calls (design D5): the bare names nobody has settled, {@value #NAME_KNOWLEDGE_BATCH} a
 * call with no book text, each answered with its most probable gender and kind ({@link NameKnowledgeReplies}). A name
 * a reply leaves out, or answers under another spelling, goes to the front of the next batch once; after the last
 * batch the ones still waiting get one call of their own, and a name never answered stays without a value. Nothing is
 * written here.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class NameKnowledgeCalls {

    /** The most bare names one call carries. */
    static final int NAME_KNOWLEDGE_BATCH = 40;

    // One item is a term, three short words and a number; the envelope and a name's length are the slack.
    private static final int BASE_TOKENS = 64;
    private static final int TOKENS_PER_ITEM = 40;
    private static final int EXPECTED_SHARE_DIVISOR = 2;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /** A name waiting to be asked, and whether it was already asked once without an answer. */
    private record Waiting(GlossaryEntry entry, boolean retried) {}

    /**
     * The entries the stage asks about: a character whose gender neither the person nor the book gave (a rule's is
     * asked too, so a disagreement can be shown) and an entry of type other, each only once, since a bare name's answer
     * does not change.
     *
     * @param held the glossary; never null
     * @return the entries to ask, in glossary order; never null
     */
    static List<GlossaryEntry> askedOf(final List<GlossaryEntry> held) {
        Objects.requireNonNull(held, "held");
        final List<GlossaryEntry> asked = held.stream()
                .filter(entry -> entry.knowledge() == null)
                .filter(entry -> entry.type() == TermType.OTHER
                        || (entry.type() == TermType.CHARACTER && !entry.hasConfirmedGender()))
                .toList();
        log.debug("Name knowledge: {} of {} entries are asked", asked.size(), held.size());
        return asked;
    }

    /**
     * Asks the model about each entry by its bare name.
     *
     * @param entries the entries to ask about; never null
     * @param frame the run's language pair; never null
     * @param calls the seam every model call goes through; never null
     * @return the kept answer per entry id, without the names never answered; or the first failed call's error
     */
    public Result<Map<String, NameKnowledge>> ask(
            final List<GlossaryEntry> entries, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        final Map<String, NameKnowledge> answers = new LinkedHashMap<>();
        if (entries.isEmpty()) {
            return Result.ok(answers);
        }
        final Deque<Waiting> waiting = new ArrayDeque<>();
        entries.forEach(entry -> waiting.addLast(new Waiting(entry, false)));
        final int planned = Math.ceilDiv(entries.size(), NAME_KNOWLEDGE_BATCH);
        log.info("Name knowledge started names={} batches={}", entries.size(), planned);
        final String system =
                templates.renderSystem(PromptName.NAME_KNOWLEDGE, frame).strip();
        int batch = 0;
        while (!waiting.isEmpty()) {
            batch++;
            final List<Waiting> asked = take(waiting);
            calls.announce(new BatchStarted(CallKind.NAME_KNOWLEDGE, batch, Math.max(batch, planned)));
            final Result<Map<String, NameKnowledge>> answered = askBatch(batch, asked, system, calls);
            if (answered.isErr()) {
                return answered;
            }
            answers.putAll(Objects.requireNonNull(answered.data(), "answered"));
            requeue(asked, answers, waiting);
        }
        log.info("Name knowledge finished names={} answered={} calls={}", entries.size(), answers.size(), batch);
        return Result.ok(answers);
    }

    /**
     * Asks about the entries of a glossary the stage asks about ({@link #askedOf}) and applies each kept answer.
     *
     * @param held the glossary as it stands; never null
     * @param frame the run's language pair; never null
     * @param calls the seam every model call goes through; never null
     * @return the answered entries as the answers leave them, in glossary order; or the first failed call's error
     */
    public Result<List<GlossaryEntry>> applied(
            final List<GlossaryEntry> held, final CallFrame frame, final ModelCalls calls) {
        final List<GlossaryEntry> asked = askedOf(held);
        return ask(asked, frame, calls).map(answers -> {
            final List<GlossaryEntry> applied = asked.stream()
                    .filter(entry -> answers.containsKey(entry.id()))
                    .map(entry -> NameKnowledgeReplies.applyTo(
                            entry, Objects.requireNonNull(answers.get(entry.id()), "answer")))
                    .toList();
            log.debug("Name knowledge applied to {} of {} asked entries", applied.size(), asked.size());
            return applied;
        });
    }

    private static List<Waiting> take(final Deque<Waiting> waiting) {
        final List<Waiting> batch = new ArrayList<>();
        while (!waiting.isEmpty() && batch.size() < NAME_KNOWLEDGE_BATCH) {
            batch.add(waiting.pollFirst());
        }
        return batch;
    }

    // A name not answered the first time goes to the front of the next batch; one not answered twice is given up.
    private static void requeue(
            final List<Waiting> asked, final Map<String, NameKnowledge> answers, final Deque<Waiting> waiting) {
        final List<Waiting> again = asked.stream()
                .filter(name -> !answers.containsKey(name.entry().id()))
                .filter(name -> !name.retried())
                .map(name -> new Waiting(name.entry(), true))
                .toList();
        final long givenUp = asked.stream()
                .filter(name -> !answers.containsKey(name.entry().id()) && name.retried())
                .count();
        log.debug("Name knowledge batch left {} to ask once more and {} without an answer", again.size(), givenUp);
        for (int index = again.size() - 1; index >= 0; index--) {
            waiting.addFirst(again.get(index));
        }
    }

    private Result<Map<String, NameKnowledge>> askBatch(
            final int batch, final List<Waiting> asked, final String system, final ModelCalls calls) {
        final List<GlossaryEntry> entries = asked.stream().map(Waiting::entry).toList();
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.NAME_KNOWLEDGE,
                List.of(),
                requestFor(batch, entries, system),
                CallDescriptor.whole(PromptName.NAME_KNOWLEDGE));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn(
                    "Name knowledge batch {} of {} names failed code={} reason={}",
                    batch,
                    entries.size(),
                    error.code(),
                    FailureReason.tokenIn(error.details()));
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Name knowledge batch {} reply {}", batch, content);
        log.debug(
                "Name knowledge batch {} asked={} askedAgain={}",
                batch,
                entries.size(),
                asked.stream().filter(Waiting::retried).count());
        return Result.ok(NameKnowledgeReplies.read(mapper, content, entries));
    }

    // One line per bare name, and a cap sized to the batch.
    private ChatRequest requestFor(final int batch, final List<GlossaryEntry> entries, final String system) {
        final String names = entries.stream().map(entry -> "- " + entry.term()).collect(Collectors.joining("\n"));
        final List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.SYSTEM, system),
                new ChatMessage(
                        ChatRole.USER,
                        templates
                                .renderUser(PromptName.NAME_KNOWLEDGE, Map.of("names", names))
                                .strip()));
        log.trace("Name knowledge batch {} messages {}", batch, messages);
        final int cap = BASE_TOKENS + TOKENS_PER_ITEM * entries.size();
        return ChatRequests.build(
                PromptName.NAME_KNOWLEDGE, messages, new OutputLimit(cap / EXPECTED_SHARE_DIVISOR, cap), false);
    }
}
