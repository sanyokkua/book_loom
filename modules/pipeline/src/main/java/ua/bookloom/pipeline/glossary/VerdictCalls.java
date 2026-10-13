package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.ReadingCounts.Read;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The two calls a reading verdict may add (15i.N5), both one term per call and labelled as term readings: the tiebreak,
 * which shows the windows where the readings found one pronoun beside those where the code counted the other, and the
 * summary at Max, which shows what each disagreeing call read. Neither answer decides by assertion: a tiebreak gender
 * stands only when a window the call showed holds its quote in the narration with a pronoun of that gender, and a
 * summary only when its quote stands in a window shown and it chose one of the readings the calls gave.
 */
@Slf4j
final class VerdictCalls {

    /** The most windows each side of a tiebreak shows. */
    static final int SIDE_WINDOWS = 3;

    /** The most windows a summary shows of each call. */
    static final int SUMMARY_WINDOWS = 2;

    // A quote of a few words and one or two labels, and the braces besides; twice that as the cap.
    private static final OutputLimit LIMIT = new OutputLimit(60, 120);
    private static final String NONE = "none";

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    VerdictCalls(final PromptTemplates templates, final ObjectMapper mapper) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Asks which pronoun refers to the term when the readings and a dominant code tally disagree.
     *
     * @param facts the term's readings and the code's evidence; never null
     * @param readers the gender the readings' majority voted for; never null
     * @param code the gender the code's tally holds dominant; never null
     * @param frame the run's language pair; never null
     * @param calls the seam the call goes through; never null
     * @return the gender a window the call showed bears out, else {@link Gender#UNKNOWN}; or the failed call's error
     */
    Result<Gender> tiebreak(
            final Facts facts, final Gender readers, final Gender code, final CallFrame frame, final ModelCalls calls) {
        final List<String> shown = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        lines.add("Reader side — a " + word(readers) + " pronoun quoted for the term");
        facts.clusterCalls().stream()
                .flatMap(call -> quotedWindows(call, readers).stream())
                .filter(side -> !shown.contains(side.text()))
                .limit(SIDE_WINDOWS)
                .forEach(
                        side -> lines.add(numbered(shown, side.text(), side.text()) + "\n    quoted: " + side.quote()));
        lines.add("Code side — a " + word(code) + " pronoun counted after the term");
        facts.evidence().windows().stream()
                .filter(window -> window.pronoun() == code && !shown.contains(window.text()))
                .limit(SIDE_WINDOWS)
                .forEach(window -> lines.add(numbered(shown, window.text(), window.text())));
        log.debug(
                "Tiebreak for entry {}: readers {} against code {}, {} windows shown",
                facts.entry().id(),
                readers,
                code,
                shown.size());
        return ask(PromptName.TERM_TIEBREAK, facts, "windows", String.join("\n", lines), frame, calls)
                .map(reply -> settled(reply, shown, facts));
    }

    /**
     * Asks at Max how the book uses a term whose reading calls disagree.
     *
     * @param facts the term's readings; never null
     * @param reads each call's majority reading, in call order; never null
     * @param frame the run's language pair; never null
     * @param calls the seam the call goes through; never null
     * @return the reading the summary chose when it is borne out, or empty; or the failed call's error
     */
    Result<Optional<Read>> summary(
            final Facts facts, final List<Read> reads, final CallFrame frame, final ModelCalls calls) {
        final List<String> shown = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        for (int index = 0; index < facts.calls().size(); index++) {
            final ReadCall call = facts.calls().get(index);
            final Read read = ReadingCounts.majority(call.reading().windows());
            if (read == null) {
                continue;
            }
            final List<Window> reading = ReadingCounts.windowsReading(call, read);
            lines.add("Call " + (index + 1) + " — " + describe(read) + " in " + reading.size() + " of "
                    + call.windows().size() + " windows");
            reading.stream()
                    .limit(SUMMARY_WINDOWS)
                    .forEach(window -> lines.add(numbered(shown, window.text(), TermReading.marked(window))));
        }
        log.debug(
                "Summary for entry {}: {} calls read {}, {} windows shown",
                facts.entry().id(),
                reads.size(),
                reads,
                shown.size());
        return ask(PromptName.TERM_SUMMARY, facts, "calls", String.join("\n", lines), frame, calls)
                .map(reply -> chosen(reply, shown, reads, facts));
    }

    /** A window of the readers' side with the words its reading quoted. */
    private record Quoted(String text, String quote) {}

    private static List<Quoted> quotedWindows(final ReadCall call, final Gender gender) {
        return call.reading().windows().stream()
                .filter(reading -> reading.vote() == gender)
                .map(reading ->
                        new Quoted(call.windows().get(reading.number() - 1).text(), reading.quote()))
                .toList();
    }

    // Numbers a window in the order shown and remembers its plain text, which a quote is checked against.
    private static String numbered(final List<String> shown, final String text, final String display) {
        shown.add(text);
        return "[" + shown.size() + "] " + display;
    }

    private Result<String> ask(
            final PromptName name,
            final Facts facts,
            final String slot,
            final String body,
            final CallFrame frame,
            final ModelCalls calls) {
        final ChatRequest request = request(name, facts, slot, body, frame);
        final Result<ChatResponse> reply =
                calls.callAbout(CallKind.TERM_READING, List.of(), request, CallDescriptor.whole(name));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn(
                    "{} call for entry {} failed code={}",
                    name.responseFormatName(),
                    facts.entry().id(),
                    error.code());
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace(
                "{} reply for {}: {}", name.responseFormatName(), facts.entry().term(), content);
        return Result.ok(content);
    }

    private ChatRequest request(
            final PromptName name, final Facts facts, final String slot, final String body, final CallFrame frame) {
        final String user = templates.renderUser(name, Map.of("term", termLine(facts), slot, body));
        final List<ChatMessage> messages = List.of(
                new ChatMessage(
                        ChatRole.SYSTEM, templates.renderSystem(name, frame).strip()),
                new ChatMessage(ChatRole.USER, user.strip()));
        log.trace(
                "{} messages for {}: {}",
                name.responseFormatName(),
                facts.entry().term(),
                messages);
        return ChatRequests.build(name, messages, LIMIT, false);
    }

    // The count, the code's tally and the other spellings, as the reading's term line shows them.
    private static String termLine(final Facts facts) {
        final String tally = facts.evidence().pronouns().compact();
        return facts.entry().term() + " — " + facts.evidence().count() + " mentions"
                + (tally.isEmpty() ? "" : " — pronouns after it: " + tally);
    }

    private Gender settled(final String reply, final List<String> shown, final Facts facts) {
        final JsonNode root = JsonReplies.tolerant(mapper, reply).orElse(null);
        if (root == null) {
            log.debug(
                    "Tiebreak reply for entry {} could not be read: the gender stays unknown",
                    facts.entry().id());
            return Gender.UNKNOWN;
        }
        final String quote = TermReadingReplies.quoteOf(root.path("quote").asText(""));
        final Gender claimed = TermReadingReplies.genderOf(root.path("gender").asText(""));
        final QuoteVote votes = QuoteVote.of(facts.analysisLanguage());
        final boolean borneOut = shown.stream().anyMatch(text -> votes.vote(quote, claimed, text) == claimed);
        log.debug(
                "Tiebreak for entry {}: claimed {} borne out by a window shown: {}",
                facts.entry().id(),
                claimed,
                borneOut && claimed != Gender.UNKNOWN);
        log.trace("Tiebreak quote for {}: {}", facts.entry().term(), quote);
        return borneOut ? claimed : Gender.UNKNOWN;
    }

    private Optional<Read> chosen(
            final String reply, final List<String> shown, final List<Read> reads, final Facts facts) {
        final JsonNode root = JsonReplies.tolerant(mapper, reply).orElse(null);
        if (root == null) {
            log.debug(
                    "Summary reply for entry {} could not be read: the windows' majority stands",
                    facts.entry().id());
            return Optional.empty();
        }
        final String quote = TermReadingReplies.quoteOf(root.path("quote").asText(""));
        final Read read = Read.of(
                TermReadingReplies.usageOf(root.path("usage").asText("")),
                TermReadingReplies.kindOf(root.path("kind").asText("")));
        final boolean stands = shown.stream().anyMatch(text -> QuoteVote.stands(quote, text));
        final boolean offered = reads.contains(read);
        log.debug(
                "Summary for entry {}: chose {}, quote stands {}, one of the calls' readings {}",
                facts.entry().id(),
                read,
                stands,
                offered);
        log.trace("Summary quote for {}: {}", facts.entry().term(), quote);
        return stands && offered ? Optional.of(read) : Optional.empty();
    }

    private static String describe(final Read read) {
        final Set<String> words = new LinkedHashSet<>();
        words.add(read.usage().wire());
        if (read.kind() != TermReading.Kind.OTHER) {
            words.add(read.kind().wire());
        }
        return String.join(", ", words);
    }

    private static String word(final Gender gender) {
        return gender == Gender.UNKNOWN ? NONE : gender.name().toLowerCase(Locale.ROOT);
    }
}
