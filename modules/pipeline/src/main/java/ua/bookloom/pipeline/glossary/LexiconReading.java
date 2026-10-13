package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.glossary.IdentityRule.Kept;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.index.WindowPlan;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Reads the recurring terms in context at the dial's plan (15i.N7). Fast puts every term to the one-sentence batch
 * choice ({@link TermChoice}); Balanced and Max send one {@code lexicon-reading} call per term, because a small model
 * mixes the windows of several terms, and the code decides from the answers ({@link LexiconReadingReplies}): keep a
 * term the book uses in one sense with its one rendering, drop an ordinary or polysemous word, promote a named thing,
 * and leave what cannot be read undecided. Nothing is written here.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class LexiconReading {

    /** What the code decided for one term. */
    public enum Verdict {
        /** The book uses the term in one sense: keep it, with the rendering the model gave. */
        KEEP,
        /** An ordinary or polysemous word: not a term. */
        DROP,
        /** A named thing: it belongs in the glossary. */
        PROMOTE,
        /** Nothing readable decided it: neither added nor removed. */
        UNDECIDED
    }

    /**
     * The decision for one term.
     *
     * @param verdict what to do with the term
     * @param rendering the one rendering that fits every window, or null when there is none or it was refused
     */
    public record Decision(Verdict verdict, @Nullable String rendering) {

        /** The decision of a term nothing readable settled. */
        public static Decision undecided() {
            return new Decision(Verdict.UNDECIDED, null);
        }
    }

    // Fast reads one sentence through the batch choice; Balanced three windows of the paragraph either side; Max four
    // of two.
    private static final WindowPlan FAST_PLAN = new WindowPlan(1, 0, 150);
    private static final WindowPlan BALANCED_PLAN = new WindowPlan(3, 1, 300);
    private static final WindowPlan MAX_PLAN = new WindowPlan(4, 2, 450);
    // A reply is a quote of a few words and a label per window, the sense count and one rendering.
    private static final int BASE_TOKENS = 48;
    private static final int TOKENS_PER_WINDOW = 64;
    // Twice the expected reply: a full answer is never cut, a reply that loops is.
    private static final int CAP_FACTOR = 2;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final TermChoice termChoice;

    /**
     * The windows the dial reads for a term.
     *
     * @param dial the quality dial; never null
     * @return how many windows, how many neighbouring segments each reads and its token budget; never null
     */
    public static WindowPlan planOf(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> FAST_PLAN;
            case BALANCED -> BALANCED_PLAN;
            case MAX -> MAX_PLAN;
        };
    }

    /**
     * Reads the terms and decides each.
     *
     * @param terms the terms as held; never null
     * @param story the slice of the book to read; never null
     * @param dial the quality dial; never null
     * @param frame the run's language pair; never null
     * @param calls the seam every model call goes through; never null
     * @return a decision for every term asked, by {@code LexiconEntry.keyOf(term)}; or the first failed call's error,
     *     in which case nothing is decided
     */
    public Result<Map<String, Decision>> read(
            final List<String> terms,
            final BookSlice story,
            final QualityDial dial,
            final CallFrame frame,
            final ModelCalls calls) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        log.info("Lexicon reading started dial={} terms={}", dial, terms.size());
        final Result<Map<String, Decision>> result = dial == QualityDial.FAST
                ? readByChoice(terms, story, frame, calls)
                : readInWindows(terms, story, planOf(dial), frame, calls);
        log.info(
                "Lexicon reading finished decided={}",
                result.isOk() ? Objects.requireNonNull(result.data()).size() : 0);
        return result;
    }

    private Result<Map<String, Decision>> readByChoice(
            final List<String> terms, final BookSlice story, final CallFrame frame, final ModelCalls calls) {
        log.debug("Lexicon reading by the batch choice: {} terms", terms.size());
        return termChoice.choose(terms, story, frame, calls).map(choice -> {
            final Map<String, Decision> decisions = new LinkedHashMap<>();
            for (final String term : terms) {
                final String key = LexiconEntry.keyOf(term);
                final Verdict verdict = choice.kept().contains(key)
                        ? Verdict.KEEP
                        : choice.dropped().contains(key) ? Verdict.DROP : Verdict.UNDECIDED;
                decisions.put(key, new Decision(verdict, null));
            }
            return decisions;
        });
    }

    private Result<Map<String, Decision>> readInWindows(
            final List<String> terms,
            final BookSlice story,
            final WindowPlan plan,
            final CallFrame frame,
            final ModelCalls calls) {
        final Map<String, Decision> decisions = new LinkedHashMap<>();
        final Set<String> seen = new HashSet<>();
        for (final String term : terms) {
            if (!seen.add(LexiconEntry.keyOf(term))) {
                continue;
            }
            final Result<Decision> decided = readOne(term, story, plan, frame, calls);
            if (decided.isErr()) {
                log.warn("Lexicon reading stopped: a call failed");
                return Result.err(Objects.requireNonNull(decided.error(), "error"));
            }
            decisions.put(LexiconEntry.keyOf(term), Objects.requireNonNull(decided.data(), "decision"));
        }
        return Result.ok(decisions);
    }

    private Result<Decision> readOne(
            final String term,
            final BookSlice story,
            final WindowPlan plan,
            final CallFrame frame,
            final ModelCalls calls) {
        final List<Mention> mentions = mentionsOf(term, story);
        final List<Window> windows = story.index().windows(mentions, plan);
        log.debug("Lexicon reading term: {} mentions, {} windows", mentions.size(), windows.size());
        if (windows.isEmpty()) {
            log.debug("Lexicon reading term has no window: no call, undecided");
            return Result.ok(Decision.undecided());
        }
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.TERM_READING,
                List.of(),
                request(term, mentions.size(), windows, frame),
                CallDescriptor.whole(PromptName.LEXICON_READING));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Lexicon reading of a term failed code={}", error.code());
            return Result.err(error);
        }
        return Result.ok(decisionOf(term, Objects.requireNonNull(reply.data(), "reply"), windows, frame));
    }

    private static List<Mention> mentionsOf(final String term, final BookSlice story) {
        final Set<Integer> slice = new LinkedHashSet<>(story.ordinals());
        return story.index().mentions(List.of(term), Case.INSENSITIVE).stream()
                .filter(mention -> slice.contains(mention.ordinal()))
                .toList();
    }

    private Decision decisionOf(
            final String term, final ChatResponse reply, final List<Window> windows, final CallFrame frame) {
        log.trace("Lexicon reading of {} reply {}", term, reply.content());
        final LexiconReadingReplies.Counts counts = LexiconReadingReplies.read(mapper, reply.content(), term, windows);
        if (counts == null) {
            return Decision.undecided();
        }
        final Verdict verdict = LexiconReadingReplies.verdictOf(counts);
        final boolean takesRendering = verdict == Verdict.KEEP || verdict == Verdict.PROMOTE;
        final String rendering = takesRendering ? renderingOf(term, counts.rendering(), frame) : null;
        log.debug(
                "Lexicon reading decided {} (terms={} words={} names={} senses={}) rendering offered={} kept={}",
                verdict,
                counts.terms(),
                counts.words(),
                counts.names(),
                counts.senses(),
                !counts.rendering().isEmpty(),
                rendering != null);
        return new Decision(verdict, rendering);
    }

    // The one identity rule (15i.N6): a rendering that is the source spelling is no translation across scripts, and
    // the suggestion step gives the term one.
    private static @Nullable String renderingOf(final String term, final String offered, final CallFrame frame) {
        if (offered.isEmpty()) {
            return null;
        }
        if (IdentityRule.refuses(term, offered, frame.analysisLanguage(), frame.targetLanguage(), Kept.NOT_KEPT)) {
            log.debug("Lexicon reading refused a rendering that is the source spelling");
            return null;
        }
        return offered;
    }

    private ChatRequest request(
            final String term, final int mentions, final List<Window> windows, final CallFrame frame) {
        final List<ChatMessage> messages = List.of(
                new ChatMessage(
                        ChatRole.SYSTEM,
                        templates
                                .renderSystem(PromptName.LEXICON_READING, frame)
                                .strip()),
                new ChatMessage(
                        ChatRole.USER,
                        templates
                                .renderUser(
                                        PromptName.LEXICON_READING,
                                        Map.of(
                                                "term",
                                                term + " — " + mentions + " mentions",
                                                "windows",
                                                TermReading.numbered(windows)))
                                .strip()));
        log.trace("Lexicon reading messages {}", messages);
        final int expected = BASE_TOKENS + TOKENS_PER_WINDOW * windows.size();
        return ChatRequests.build(
                PromptName.LEXICON_READING, messages, new OutputLimit(expected, expected * CAP_FACTOR), false);
    }
}
