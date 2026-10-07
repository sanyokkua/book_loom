package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermEvidence.Evidence;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.NameRules;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The model's suggested target for each open glossary entry, the third step of the review and of the name scan, after
 * the verdicts: a call of its own, since a small model does one task per call far better than two. The rendering
 * follows the Book Brief's name policy — under Keep original a name keeps its source spelling without any call, and
 * only a term is asked about. Nothing is written here; the caller writes once every batch has answered.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SuggestTargets {

    /** How many entries one call carries, small enough for a small model to answer every one. */
    static final int BATCH_SIZE = 20;

    /** An example sentence longer than this is cut, so the batch stays short. */
    static final int MAX_EXAMPLE_CHARS = 120;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /**
     * The entries with the targets the model suggests for them written in, for entries the glossary does not hold yet —
     * the name scan's confirmed proposals — so nothing is written here either.
     *
     * @param entries the entries to give a target; never null
     * @param segments the book's body segments; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @return the entries in their order, each with its suggested target where the model gave one; or the first failed
     *     call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final List<GlossaryEntry> entries,
            final List<Segment> segments,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        return suggest(entries, segments, frame, policy, calls).map(answered -> {
            final Map<String, Suggestion> byId = new HashMap<>();
            answered.forEach(suggestion -> byId.putIfAbsent(suggestion.entry().id(), suggestion));
            log.debug("Suggested targets for {} of {} entries", byId.size(), entries.size());
            return entries.stream()
                    .map(entry -> Optional.ofNullable(byId.get(entry.id()))
                            .map(suggestion -> suggestion.onto(entry))
                            .orElse(entry))
                    .toList();
        });
    }

    /**
     * Suggests a target for each entry.
     *
     * @param entries the open entries, with the type and gender the verdicts gave them; never null
     * @param segments the book's body segments, where each entry's example sentence is read; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @return the suggestions, at most one per entry and none for an entry the model left without one; or the first
     *     failed call's error
     */
    Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> entries,
            final List<Segment> segments,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(calls, "calls");
        final List<Suggestion> kept = new ArrayList<>(keptAsWritten(entries, policy));
        final List<GlossaryEntry> asked = entries.stream()
                .filter(entry -> !isKeptAsWritten(entry, policy))
                .toList();
        final int batches = (asked.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        log.info(
                "Glossary suggestions started policy={} entries={} kept={} asked={} batches={}",
                policy,
                entries.size(),
                kept.size(),
                asked.size(),
                batches);
        final Result<List<Suggestion>> answered = ask(asked, segments, frame, policy, calls);
        if (answered.isErr()) {
            log.warn("Glossary suggestions failed; nothing is suggested");
            return answered;
        }
        kept.addAll(Objects.requireNonNull(answered.data(), "answered"));
        log.info("Glossary suggestions finished suggested={} of {}", kept.size(), entries.size());
        return Result.ok(kept);
    }

    /** Under Keep original a name keeps its source spelling, so only a term needs the model. */
    private static boolean isKeptAsWritten(final GlossaryEntry entry, final NamePolicy policy) {
        return policy == NamePolicy.KEEP_ORIGINAL && entry.type() != TermType.TERM;
    }

    private static List<Suggestion> keptAsWritten(final List<GlossaryEntry> entries, final NamePolicy policy) {
        return entries.stream()
                .filter(entry -> isKeptAsWritten(entry, policy))
                .map(entry -> new Suggestion(entry, entry.term(), Gender.UNKNOWN))
                .toList();
    }

    private Result<List<Suggestion>> ask(
            final List<GlossaryEntry> asked,
            final List<Segment> segments,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        if (asked.isEmpty()) {
            return Result.ok(List.of());
        }
        final Map<String, Evidence> evidence = TermEvidence.of(
                segments, asked.stream().map(GlossaryEntry::term).toList());
        final String system = templates
                .renderSystem(
                        PromptName.SUGGEST_TARGETS,
                        frame,
                        Map.of("nameRule", NameRules.bundled().rule(policy, frame.targetLanguage())))
                .strip();
        final Script refused = refusedScript(frame, policy);
        final Script required = requiredScript(frame);
        final int batches = (asked.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        final List<Suggestion> suggestions = new ArrayList<>();
        for (int index = 0; index < batches; index++) {
            final List<GlossaryEntry> batch =
                    asked.subList(index * BATCH_SIZE, Math.min(asked.size(), (index + 1) * BATCH_SIZE));
            calls.announce(new BatchStarted(CallKind.SUGGEST_TARGETS, index + 1, batches));
            final Result<List<Suggestion>> answered =
                    runBatch(index, batch, evidence, system, refused, required, calls);
            if (answered.isErr()) {
                return answered;
            }
            suggestions.addAll(Objects.requireNonNull(answered.data(), "suggestions"));
        }
        return Result.ok(suggestions);
    }

    private Result<List<Suggestion>> runBatch(
            final int index,
            final List<GlossaryEntry> batch,
            final Map<String, Evidence> evidence,
            final String system,
            @Nullable final Script refused,
            @Nullable final Script required,
            final ModelCalls calls) {
        final String lines = String.join(
                "\n",
                batch.stream()
                        .map(entry -> line(entry, evidence.getOrDefault(entry.term(), Evidence.NONE)))
                        .toList());
        final List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.SYSTEM, system),
                new ChatMessage(
                        ChatRole.USER,
                        templates
                                .renderUser(PromptName.SUGGEST_TARGETS, Map.of("terms", lines))
                                .strip()));
        final ChatRequest request = ChatRequests.build(
                PromptName.SUGGEST_TARGETS, messages, OutputLimit.forSuggestions(batch.size()), false);
        log.trace("Glossary suggestion batch {} messages {}", index, messages);
        final Result<ChatResponse> reply = calls.call(CallKind.SUGGEST_TARGETS, null, request);
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Glossary suggestion batch {} of size {} failed code={}", index, batch.size(), error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Glossary suggestion batch {} reply {}", index, content);
        final Map<String, GlossaryEntry> byKey = new LinkedHashMap<>();
        batch.forEach(entry -> byKey.put(GlossaryKeys.of(entry.term()), entry));
        final List<Suggestion> read = SuggestionReplies.read(mapper, content, byKey, refused, required);
        log.debug("Glossary suggestion batch {} answered: size={} suggestions={}", index, batch.size(), read.size());
        return Result.ok(read);
    }

    /**
     * The script a suggested name must not stay in: under Transliterate, the source's, when the target is written in
     * another one — a copied name is no suggestion there. Under Translate a kept foreign name can be right.
     */
    private static @Nullable Script refusedScript(final CallFrame frame, final NamePolicy policy) {
        if (policy != NamePolicy.TRANSLITERATE) {
            return null;
        }
        final Optional<Script> source = Languages.scriptOf(frame.sourceLanguage());
        final Optional<Script> target = Languages.scriptOf(frame.targetLanguage());
        final boolean differs = source.isPresent() && target.isPresent() && source.get() != target.get();
        log.debug("Suggestion script rule source={} target={} refusesSourceScript={}", source, target, differs);
        return differs ? source.get() : null;
    }

    /** The target language's script when it has letters of its own: every letter of a suggestion must be one. */
    private static @Nullable Script requiredScript(final CallFrame frame) {
        final Optional<Script> target = Languages.scriptOf(frame.targetLanguage())
                .filter(script -> !script.letterScripts().isEmpty());
        log.debug("Suggestion required script {}", target);
        return target.orElse(null);
    }

    private static String line(final GlossaryEntry entry, final Evidence evidence) {
        final String gender = entry.gender() == Gender.UNKNOWN
                ? ""
                : ", " + entry.gender().name().toLowerCase(Locale.ROOT);
        final String example = evidence.examples().isEmpty()
                ? ""
                : " — \"" + cut(evidence.examples().getFirst()) + "\"";
        return "- " + entry.term() + " — " + typeOf(entry.type()) + gender + example;
    }

    private static String cut(final String example) {
        return example.length() > MAX_EXAMPLE_CHARS ? example.substring(0, MAX_EXAMPLE_CHARS) + "…" : example;
    }

    private static String typeOf(final TermType type) {
        return switch (type) {
            case CHARACTER -> "person";
            case PLACE -> "place";
            case TERM -> "term";
            case TITLE -> "title";
            case OTHER -> "other";
        };
    }
}
