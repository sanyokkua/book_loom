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
import java.util.Optional;
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
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.PreScanReplies.Proposal;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The model's name scan, run only when the person asks for it: it finds names a frequency count misses and guesses
 * their type and gender, but it costs time and a provider call. Nothing is written until every batch has answered, so
 * a failed call never wipes what the person already has.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class PreScan {

    /** How many candidates one call carries, so a long book stays inside the model's context. */
    static final int BATCH_SIZE = 40;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final GlossaryRepository glossary;

    /**
     * Asks the model about the book's candidate names and adds what it proposes to the glossary.
     *
     * @param projectId the project whose glossary receives the entries; never null
     * @param segments the segments whose capitalised words are the candidates; never null, may be empty
     * @param frame the run's language pair and style, for the system message
     * @param calls the seam every model call goes through
     * @return the entries added, unlocked and with no target — a term the glossary holds or the person removed is not
     *     among them; or the error of the first failed call or glossary access, with nothing added by the model's
     *     answers
     */
    public Result<List<GlossaryEntry>> scan(
            final String projectId, final List<Segment> segments, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        try {
            return run(projectId, segments, frame, calls);
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Name scan failed", "The model name scan could not be completed.", null, cause);
            log.error("Unexpected pre-scan failure project={} code={}", projectId, error.code(), cause);
            return Result.err(error);
        }
    }

    private Result<List<GlossaryEntry>> run(
            final String projectId, final List<Segment> segments, final CallFrame frame, final ModelCalls calls) {
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
                merge(projectId, Objects.requireNonNull(proposals.data(), "proposals"));
        log.info("Model pre-scan finished project={} ok={} entriesAdded={}", projectId, merged.isOk(), added(merged));
        return merged;
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
        final Result<ChatResponse> reply = calls.call(CallKind.PRESCAN, null, request);
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
        final String system = templates
                .renderSystem(PromptName.PRESCAN, frame.systemSlotValues())
                .strip();
        final String user = templates
                .renderUser(PromptName.PRESCAN, Map.of("candidates", lines, "existingTerms", existing))
                .strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    private Result<List<GlossaryEntry>> merge(final String projectId, final Collection<Proposal> proposals) {
        final Result<List<GlossaryEntry>> held = glossary.all(projectId);
        if (held.isErr()) {
            return held;
        }
        final Set<String> heldKeys = new HashSet<>();
        Objects.requireNonNull(held.data(), "held").forEach(entry -> heldKeys.add(PreScanReplies.key(entry.term())));
        final List<Proposal> fresh = proposals.stream()
                .filter(proposal -> !heldKeys.contains(
                        PreScanReplies.key(proposal.candidate().term())))
                .toList();
        final List<GlossaryEntry> added = new ArrayList<>();
        for (final Proposal proposal : fresh) {
            final Result<Optional<GlossaryEntry>> stored = addUnlessRemoved(projectId, proposal);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            Objects.requireNonNull(stored.data(), "stored").ifPresent(added::add);
        }
        log.debug(
                "Pre-scan merge project={}: {} proposed, {} held, {} removed, {} added",
                projectId,
                proposals.size(),
                proposals.size() - fresh.size(),
                fresh.size() - added.size(),
                added.size());
        return Result.ok(added);
    }

    private Result<Optional<GlossaryEntry>> addUnlessRemoved(final String projectId, final Proposal proposal) {
        final Result<Boolean> removed =
                glossary.wasRemoved(projectId, proposal.candidate().term());
        if (removed.isErr()) {
            return Result.err(Objects.requireNonNull(removed.error(), "error"));
        }
        if (Boolean.TRUE.equals(removed.data())) {
            log.trace(
                    "Pre-scan proposal {} skipped: the person removed it",
                    proposal.candidate().term());
            return Result.ok(Optional.empty());
        }
        final Result<GlossaryEntry> stored = glossary.add(entryOf(projectId, proposal));
        return stored.map(Optional::of);
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
