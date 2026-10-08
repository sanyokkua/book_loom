package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.glossary.TermEvidence.Evidence;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The model's choice among recurring words: of candidate words and phrases, each shown with how often the book uses it
 * and a sentence that holds it, which ones a translator must render the same way every time. The same call serves the
 * scan (candidates from the text) and the review (the terms already held). Nothing is written here.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TermChoice {

    /**
     * The model's verdicts.
     *
     * @param kept the lexicon keys of the terms it said to keep
     * @param dropped the lexicon keys of the terms it explicitly said to drop; a term it did not mention is in neither
     * @param undecided the lexicon keys of the terms whose batch was answered all alike even in halves, so the model's
     *     verdict on them is not taken: a scan does not add them and a review does not remove them
     */
    public record Choice(Set<String> kept, Set<String> dropped, Set<String> undecided) {

        /** Copies the sets. */
        public Choice {
            kept = Set.copyOf(kept);
            dropped = Set.copyOf(dropped);
            undecided = Set.copyOf(undecided);
        }

        /** A choice that decided every term it was asked about. */
        public Choice(final Set<String> kept, final Set<String> dropped) {
            this(kept, dropped, Set.of());
        }
    }

    private static final int BATCH_SIZE = 10;
    private static final int UNIFORM_MIN = 6;
    private static final int TOKENS_PER_VERDICT = 24;
    private static final int BASE_TOKENS = 96;
    private static final int MAX_EXAMPLE_CHARS = 140;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /**
     * Asks the model which of the terms to keep.
     *
     * @param terms the non-null candidate terms, in the spelling the book uses
     * @param segments the book's body segments, where each term's example is read
     * @param frame the run's language pair and style
     * @param calls the seam every model call goes through
     * @return the verdicts as lexicon keys; the first failed call's error otherwise, and {@code validation} when an
     *     answer cannot be read, since a half-read answer must not decide what is removed
     */
    public Result<Choice> choose(
            final List<String> terms, final List<Segment> segments, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        if (terms.isEmpty()) {
            return Result.ok(new Choice(Set.of(), Set.of()));
        }
        final Map<String, Evidence> evidence = TermEvidence.of(segments, terms);
        final String system =
                templates.renderSystem(PromptName.TERM_CHOICE, frame).strip();
        return runBatches(terms, evidence, system, calls);
    }

    private Result<Choice> runBatches(
            final List<String> terms,
            final Map<String, Evidence> evidence,
            final String system,
            final ModelCalls calls) {
        final int batches = (terms.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        log.info("Term choice started terms={} batches={}", terms.size(), batches);
        final Set<String> kept = new HashSet<>();
        final Set<String> dropped = new HashSet<>();
        final Set<String> undecided = new HashSet<>();
        for (int index = 0; index < batches; index++) {
            final List<String> batch =
                    terms.subList(index * BATCH_SIZE, Math.min(terms.size(), (index + 1) * BATCH_SIZE));
            calls.announce(new BatchStarted(CallKind.REVIEW_TERMS, index + 1, batches));
            final Result<Choice> answered = runBatch(batch, evidence, system, calls);
            if (answered.isErr()) {
                return answered;
            }
            final Choice batchChoice = Objects.requireNonNull(answered.data(), "choice");
            kept.addAll(batchChoice.kept());
            dropped.addAll(batchChoice.dropped());
            undecided.addAll(batchChoice.undecided());
        }
        log.info(
                "Term choice finished kept={} dropped={} undecided={} of {}",
                kept.size(),
                dropped.size(),
                undecided.size(),
                terms.size());
        return Result.ok(new Choice(kept, dropped, undecided));
    }

    private Result<Choice> runBatch(
            final List<String> batch,
            final Map<String, Evidence> evidence,
            final String system,
            final ModelCalls calls) {
        final Result<Choice> first = ask(batch, evidence, system, calls);
        if (first.isErr() || !isUniform(Objects.requireNonNull(first.data(), "choice"))) {
            return first;
        }
        log.warn("Term choice batch of {} answered all alike; asking again in halves", batch.size());
        final int middle = batch.size() / 2;
        final Result<Choice> head = ask(batch.subList(0, middle), evidence, system, calls);
        if (head.isErr()) {
            return head;
        }
        final Result<Choice> tail = ask(batch.subList(middle, batch.size()), evidence, system, calls);
        if (tail.isErr()) {
            return tail;
        }
        final Set<String> kept =
                new HashSet<>(Objects.requireNonNull(head.data(), "head").kept());
        final Set<String> dropped = new HashSet<>(head.data().dropped());
        kept.addAll(Objects.requireNonNull(tail.data(), "tail").kept());
        dropped.addAll(tail.data().dropped());
        final Choice merged = new Choice(kept, dropped);
        if (isUniform(merged)) {
            // Only this batch's verdicts are doubted; the other batches were read and stand.
            log.warn("Term choice batch of {} is still answered all alike; it is left undecided", batch.size());
            return Result.ok(new Choice(
                    Set.of(),
                    Set.of(),
                    Set.copyOf(batch.stream().map(LexiconEntry::keyOf).toList())));
        }
        return Result.ok(merged);
    }

    private static boolean isUniform(final Choice choice) {
        return choice.kept().size() + choice.dropped().size() >= UNIFORM_MIN
                && (choice.kept().isEmpty() || choice.dropped().isEmpty());
    }

    private Result<Choice> ask(
            final List<String> batch,
            final Map<String, Evidence> evidence,
            final String system,
            final ModelCalls calls) {
        final String lines = String.join(
                "\n",
                batch.stream()
                        .map(term -> line(term, evidence.getOrDefault(term, Evidence.NONE)))
                        .toList());
        final List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.SYSTEM, system),
                new ChatMessage(
                        ChatRole.USER,
                        templates
                                .renderUser(PromptName.TERM_CHOICE, Map.of("terms", lines))
                                .strip()));
        final int cap = BASE_TOKENS + TOKENS_PER_VERDICT * batch.size();
        final ChatRequest request =
                ChatRequests.build(PromptName.TERM_CHOICE, messages, new OutputLimit(cap / 2, cap), false);
        log.trace("Term choice messages {}", messages);
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.REVIEW_TERMS, List.of(), request, CallDescriptor.whole(PromptName.TERM_CHOICE));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Term choice batch of {} failed code={}", batch.size(), error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Term choice reply {}", content);
        return verdicts(content, batch);
    }

    private Result<Choice> verdicts(final String reply, final List<String> asked) {
        final Map<String, String> byKey = new LinkedHashMap<>();
        asked.forEach(term -> byKey.put(LexiconEntry.keyOf(term), term));
        final Set<String> kept = new HashSet<>();
        final Set<String> dropped = new HashSet<>();
        final int from = reply.indexOf('{');
        final int to = reply.lastIndexOf('}');
        try {
            final JsonNode terms = from < 0 || to <= from
                    ? null
                    : mapper.readTree(reply.substring(from, to + 1)).path("terms");
            if (terms == null || !terms.isArray()) {
                return unreadable();
            }
            for (final JsonNode verdict : terms) {
                final String key = LexiconEntry.keyOf(verdict.path("term").asText(""));
                if (byKey.containsKey(key) && verdict.has("keep")) {
                    (verdict.path("keep").asBoolean(false) ? kept : dropped).add(key);
                }
            }
        } catch (IOException cut) {
            return unreadable();
        }
        log.debug("Term choice batch answered asked={} kept={} dropped={}", asked.size(), kept.size(), dropped.size());
        return Result.ok(new Choice(kept, dropped));
    }

    private static Result<Choice> unreadable() {
        log.warn("Term choice reply could not be read; nothing is decided");
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Unreadable answer",
                "The model's answer could not be read; nothing was changed."));
    }

    private static String line(final String term, final Evidence evidence) {
        final String example = evidence.examples().isEmpty()
                ? ""
                : " — \"" + cut(evidence.examples().getFirst()) + "\"";
        final String uses = evidence.count() > 0 ? " · " + evidence.count() + " uses" : "";
        return "- " + term + uses + example;
    }

    private static String cut(final String example) {
        return example.length() > MAX_EXAMPLE_CHARS ? example.substring(0, MAX_EXAMPLE_CHARS) + "…" : example;
    }
}
