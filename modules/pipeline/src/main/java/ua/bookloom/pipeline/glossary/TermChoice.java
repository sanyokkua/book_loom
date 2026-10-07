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

    private static final int BATCH_SIZE = 40;
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
     * @return the terms the model said to keep, as lexicon keys; the first failed call's error otherwise. A term the
     *     model did not mention counts as dropped.
     */
    public Result<Set<String>> choose(
            final List<String> terms, final List<Segment> segments, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        if (terms.isEmpty()) {
            return Result.ok(Set.of());
        }
        final Map<String, Evidence> evidence = TermEvidence.of(segments, terms);
        final String system =
                templates.renderSystem(PromptName.TERM_CHOICE, frame).strip();
        final int batches = (terms.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        log.info("Term choice started terms={} batches={}", terms.size(), batches);
        final Set<String> kept = new HashSet<>();
        for (int index = 0; index < batches; index++) {
            final List<String> batch =
                    terms.subList(index * BATCH_SIZE, Math.min(terms.size(), (index + 1) * BATCH_SIZE));
            calls.announce(new BatchStarted(CallKind.REVIEW_TERMS, index + 1, batches));
            final Result<Set<String>> answered = runBatch(batch, evidence, system, calls);
            if (answered.isErr()) {
                return answered;
            }
            kept.addAll(Objects.requireNonNull(answered.data(), "kept"));
        }
        log.info("Term choice finished kept={} of {}", kept.size(), terms.size());
        return Result.ok(kept);
    }

    private Result<Set<String>> runBatch(
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
        final Result<ChatResponse> reply = calls.call(CallKind.REVIEW_TERMS, null, request);
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Term choice batch of {} failed code={}", batch.size(), error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Term choice reply {}", content);
        return Result.ok(kept(content, batch));
    }

    private Set<String> kept(final String reply, final List<String> asked) {
        final Map<String, String> byKey = new LinkedHashMap<>();
        asked.forEach(term -> byKey.put(LexiconEntry.keyOf(term), term));
        final Set<String> kept = new HashSet<>();
        final int from = reply.indexOf('{');
        final int to = reply.lastIndexOf('}');
        if (from < 0 || to <= from) {
            log.debug("Term choice reply is not JSON; nothing is kept");
            return kept;
        }
        try {
            final JsonNode terms =
                    mapper.readTree(reply.substring(from, to + 1)).path("terms");
            for (final JsonNode verdict : terms) {
                final String key = LexiconEntry.keyOf(verdict.path("term").asText(""));
                if (verdict.path("keep").asBoolean(false) && byKey.containsKey(key)) {
                    kept.add(key);
                }
            }
        } catch (IOException unreadable) {
            log.debug("Term choice reply is not readable; nothing is kept");
        }
        log.debug("Term choice batch answered asked={} kept={}", asked.size(), kept.size());
        return kept;
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
