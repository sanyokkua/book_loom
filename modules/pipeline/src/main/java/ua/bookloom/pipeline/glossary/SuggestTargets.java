package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Refusal;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Replies;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermEvidence.Evidence;
import ua.bookloom.pipeline.prompt.CallDescriptor;
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

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /**
     * The entries with the targets the model suggests for them written in, for entries the glossary does not hold yet —
     * the name scan's confirmed proposals — so nothing is written here either.
     *
     * @param entries the entries to give a target; never null
     * @param story the book's body segments; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @param context the held glossary and the entries' readings, which the names' clusters and flags come from;
     *     never null
     * @return the entries in their order, each with its suggested target where the model gave one; or the first failed
     *     call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls,
            final TargetContext context) {
        return suggest(entries, story, frame, policy, calls, context).map(answered -> {
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
     * The entries with the targets the model suggests for them written in, knowing nothing of the glossary beyond the
     * entries.
     *
     * @param entries the entries to give a target; never null
     * @param story the book's body segments; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @return the entries in their order, each with its suggested target where the model gave one; or the first failed
     *     call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        return suggestOnto(entries, story, frame, policy, calls, TargetContext.none());
    }

    /**
     * Suggests a target for each entry, with the glossary and the readings the entries belong to (15i.N6).
     *
     * @param entries the open entries, with the type and gender the verdicts gave them; never null
     * @param story the book's body segments, where each entry's example sentences are read; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @param context the held glossary and the entries' readings; never null
     * @return the suggestions, at most one per entry and none for an entry the model left without one or answered
     *     twice with a refused rendering; or the first failed call's error
     */
    Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls,
            final TargetContext context) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(context, "context");
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
        final Result<List<Suggestion>> answered = ask(asked, new Setting(story, frame, policy, calls, context));
        if (answered.isErr()) {
            log.warn("Glossary suggestions failed; nothing is suggested");
            return answered;
        }
        kept.addAll(Objects.requireNonNull(answered.data(), "answered"));
        final List<Suggestion> locked = SuggestionLocks.apply(entries, kept, frame, policy, context);
        log.info("Glossary suggestions finished suggested={} of {}", locked.size(), entries.size());
        return Result.ok(locked);
    }

    /**
     * Suggests a target for each entry, knowing nothing of the glossary beyond the entries.
     *
     * @param entries the open entries, with the type and gender the verdicts gave them; never null
     * @param story the book's body segments, where each entry's example sentences are read; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @return the suggestions, at most one per entry and none for an entry the model left without one; or the first
     *     failed call's error
     */
    Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        return suggest(entries, story, frame, policy, calls, TargetContext.none());
    }

    /** Under Keep original a name keeps its source spelling, so only a term needs the model. */
    private static boolean isKeptAsWritten(final GlossaryEntry entry, final NamePolicy policy) {
        return policy == NamePolicy.KEEP_ORIGINAL && entry.type() != TermType.TERM;
    }

    private static List<Suggestion> keptAsWritten(final List<GlossaryEntry> entries, final NamePolicy policy) {
        return entries.stream()
                .filter(entry -> isKeptAsWritten(entry, policy))
                .map(entry -> new Suggestion(entry, entry.term()))
                .toList();
    }

    /** What every call of one suggestion run shares. */
    private record Setting(
            BookSlice story, CallFrame frame, NamePolicy policy, ModelCalls calls, TargetContext context) {}

    /** What one run asks and checks with, built once for all its batches. */
    private record Run(
            Setting setting,
            String system,
            Map<String, Evidence> evidence,
            @Nullable Script refused,
            @Nullable Script required,
            RenderingChecks checks,
            ClusterAgreement cluster) {}

    private Result<List<Suggestion>> ask(final List<GlossaryEntry> asked, final Setting setting) {
        if (asked.isEmpty()) {
            return Result.ok(List.of());
        }
        final Run run = runOf(asked, setting);
        final int batches = (asked.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        final List<Suggestion> suggestions = new ArrayList<>();
        for (int index = 0; index < batches; index++) {
            final List<GlossaryEntry> batch =
                    asked.subList(index * BATCH_SIZE, Math.min(asked.size(), (index + 1) * BATCH_SIZE));
            setting.calls().announce(new BatchStarted(CallKind.SUGGEST_TARGETS, index + 1, batches));
            final Result<List<Suggestion>> answered = runBatch(index, batch, run);
            if (answered.isErr()) {
                return answered;
            }
            suggestions.addAll(Objects.requireNonNull(answered.data(), "suggestions"));
        }
        return Result.ok(run.cluster().align(suggestions));
    }

    private Run runOf(final List<GlossaryEntry> asked, final Setting setting) {
        final CallFrame frame = setting.frame();
        final String system = templates
                .renderSystem(
                        PromptName.SUGGEST_TARGETS,
                        frame,
                        Map.of("nameRule", NameRules.bundled().rule(setting.policy(), frame.targetLanguage())))
                .strip();
        final List<GlossaryEntry> glossary =
                glossaryWith(asked, setting.context().held());
        final NameClusters clusters = NameClusters.of(glossary, setting.story().index(), frame.analysisLanguage());
        return new Run(
                setting,
                system,
                evidenceOf(setting.story(), asked),
                refusedScript(frame, setting.policy()),
                requiredScript(frame),
                new RenderingChecks(frame, setting.policy(), setting.context().readings()),
                ClusterAgreement.over(clusters, setting.context().held(), asked));
    }

    // The held entries, each asked one standing in for its held version: a cluster is read from what the call knows.
    private static List<GlossaryEntry> glossaryWith(final List<GlossaryEntry> asked, final List<GlossaryEntry> held) {
        final Map<String, GlossaryEntry> byId = new LinkedHashMap<>();
        held.forEach(entry -> byId.put(entry.id(), entry));
        asked.forEach(entry -> byId.put(entry.id(), entry));
        return List.copyOf(byId.values());
    }

    private static Map<String, Evidence> evidenceOf(final BookSlice story, final List<GlossaryEntry> asked) {
        return TermEvidence.of(
                story.index(),
                story.ordinals(),
                asked.stream().map(GlossaryEntry::term).toList());
    }

    // A batch is asked once; the renderings the letter table or the identity rule refuse are asked once more, each
    // with what was refused, and one refused again is left without a target.
    private Result<List<Suggestion>> runBatch(final int index, final List<GlossaryEntry> batch, final Run run) {
        final Result<Replies> first = exchange(index, batch, Map.of(), run);
        if (first.isErr()) {
            return Result.err(Objects.requireNonNull(first.error(), "error"));
        }
        final Replies replies = Objects.requireNonNull(first.data(), "replies");
        final List<Suggestion> kept = new ArrayList<>(replies.kept());
        final Map<String, Refusal> refused = refusedOf(replies);
        if (refused.isEmpty()) {
            return Result.ok(kept);
        }
        log.info("Suggestion batch {}: {} renderings refused, asked once more", index, refused.size());
        final List<GlossaryEntry> again =
                refused.values().stream().map(Refusal::entry).toList();
        final Result<Replies> second = exchange(index, again, refused, run);
        if (second.isErr()) {
            return Result.err(Objects.requireNonNull(second.error(), "error"));
        }
        final List<Suggestion> retried =
                Objects.requireNonNull(second.data(), "replies").kept();
        log.info("Suggestion batch {}: {} of {} refused renderings replaced", index, retried.size(), refused.size());
        kept.addAll(retried);
        return Result.ok(kept);
    }

    // The refusals of a reply by entry id, leaving out an entry the same reply also answered acceptably.
    private static Map<String, Refusal> refusedOf(final Replies replies) {
        final Set<String> answered = new HashSet<>();
        replies.kept().forEach(suggestion -> answered.add(suggestion.entry().id()));
        final Map<String, Refusal> refused = new LinkedHashMap<>();
        replies.refused().stream()
                .filter(refusal -> !answered.contains(refusal.entry().id()))
                .forEach(refusal -> refused.putIfAbsent(refusal.entry().id(), refusal));
        return refused;
    }

    private Result<Replies> exchange(
            final int index, final List<GlossaryEntry> batch, final Map<String, Refusal> refused, final Run run) {
        final ChatRequest request = requestFor(index, batch, refused, run);
        final Result<ChatResponse> reply = run.setting()
                .calls()
                .callAbout(
                        CallKind.SUGGEST_TARGETS, List.of(), request, CallDescriptor.whole(PromptName.SUGGEST_TARGETS));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Glossary suggestion batch {} of size {} failed code={}", index, batch.size(), error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Glossary suggestion batch {} reply {}", index, content);
        final Map<String, GlossaryEntry> byKey = new LinkedHashMap<>();
        batch.forEach(entry -> byKey.put(GlossaryKeys.of(entry.term()), entry));
        final Replies read =
                SuggestionReplies.read(mapper, content, byKey, run.refused(), run.required(), run.checks()::standing);
        log.debug(
                "Glossary suggestion batch {} answered: size={} suggestions={} refused={}",
                index,
                batch.size(),
                read.kept().size(),
                read.refused().size());
        return Result.ok(read);
    }

    private ChatRequest requestFor(
            final int index, final List<GlossaryEntry> batch, final Map<String, Refusal> refused, final Run run) {
        final Setting setting = run.setting();
        final String lines = batch.stream()
                .map(entry -> SuggestionLines.of(
                        entry,
                        run.evidence().getOrDefault(entry.term(), Evidence.NONE),
                        setting.context().readings().get(entry.id()),
                        run.cluster().statementFor(entry),
                        refused.get(entry.id())))
                .collect(Collectors.joining("\n"));
        final List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.SYSTEM, run.system()),
                new ChatMessage(
                        ChatRole.USER,
                        templates
                                .renderUser(PromptName.SUGGEST_TARGETS, Map.of("terms", lines))
                                .strip()));
        log.trace("Glossary suggestion batch {} messages {}", index, messages);
        return ChatRequests.build(
                PromptName.SUGGEST_TARGETS, messages, OutputLimit.forSuggestions(batch.size()), false);
    }

    /**
     * The script a suggested name must not stay in: under Transliterate, the source's, when the target is written in
     * another one — a copied name is no suggestion there. Under Translate a kept foreign name can be right.
     */
    private static @Nullable Script refusedScript(final CallFrame frame, final NamePolicy policy) {
        if (policy != NamePolicy.TRANSLITERATE) {
            return null;
        }
        final Optional<Script> source = Languages.scriptOf(frame.analysisLanguage());
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
}
