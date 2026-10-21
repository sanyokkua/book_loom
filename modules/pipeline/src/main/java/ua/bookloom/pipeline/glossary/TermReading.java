package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The model reads one glossary term in numbered windows from across the book (15i.N4): per window how the term is
 * used, what it names and which third-person pronoun of the narration refers to it, quoted; per term whether it is a
 * speaking name and a brand. One term per call, because a small model mixes the windows of several terms; the code
 * then keeps a gender vote only where the quote bears it out ({@link TermReadingReplies}). Nothing is decided or
 * written here.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TermReading {

    // Per window a quote of a few words and three short labels; the term's two flags and the braces besides.
    private static final int BASE_TOKENS = 40;
    private static final int TOKENS_PER_WINDOW = 80;
    // Twice the expected reply: a full answer is never cut, a reply that loops is.
    private static final int CAP_FACTOR = 2;
    private static final String MARK_OPEN = "[[";
    private static final String MARK_CLOSE = "]]";

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /** How a window uses the term. */
    public enum Usage {
        /** A proper name. */
        NAME("name"),
        /** An ordinary word. */
        WORD("word"),
        /** A part of a longer name: a street, a firm, a ship named after someone. */
        PART_OF_NAME("part-of-name"),
        /** A title or a form of address. */
        TITLE("title"),
        /** No usage the schema lists. */
        UNSURE("");

        private final String wire;

        Usage(final String wire) {
            this.wire = wire;
        }

        /**
         * The word the schema uses for this usage.
         *
         * @return the schema's word; empty for {@link #UNSURE}, which the schema does not list
         */
        public String wire() {
            return wire;
        }
    }

    /** What the term names in a window. */
    public enum Kind {
        /** A person or a person-like character. */
        PERSON("person"),
        /** A place. */
        PLACE("place"),
        /** An organisation, a group or a company. */
        ORGANISATION("organisation"),
        /** A named thing: an object, a ship, a product. */
        THING("thing"),
        /** Anything else, or no kind the schema lists. */
        OTHER("other");

        private final String wire;

        Kind(final String wire) {
            this.wire = wire;
        }

        /**
         * The word the schema uses for this kind.
         *
         * @return the schema's word
         */
        public String wire() {
            return wire;
        }
    }

    /**
     * The term a call reads and what the code already knows of it.
     *
     * @param term the term as held
     * @param mentions how many times the story writes it, case-sensitive
     * @param tally the pronouns the code read after the term (its cluster's pooled)
     * @param cluster the other spellings of the same person, empty when it stands alone
     */
    public record Subject(String term, int mentions, PronounGender.Evidence tally, List<String> cluster) {

        /** Copies the cluster. */
        public Subject {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(tally, "tally");
            cluster = List.copyOf(cluster);
        }
    }

    /**
     * What the model said of one window.
     *
     * @param number the window's number in the call, from 1
     * @param quote the words the model quoted, without the term's marks; empty when it quoted none
     * @param usage how the window uses the term
     * @param kind what the term names there
     * @param claimed the gender the model claimed for the quoted pronoun; {@link Gender#UNKNOWN} for "none"
     * @param vote the claimed gender when the quote stands as evidence, else {@link Gender#UNKNOWN}
     */
    public record WindowReading(int number, String quote, Usage usage, Kind kind, Gender claimed, Gender vote) {}

    /**
     * The reading of one term in one call.
     *
     * @param term the term read
     * @param windows the windows the reply answered, by number, each once
     * @param speaking whether the model says the name's meaning matters to the story
     * @param brand whether the model says the term is a brand or a product
     */
    public record Reading(String term, List<WindowReading> windows, boolean speaking, boolean brand) {

        /** Copies the windows. */
        public Reading {
            Objects.requireNonNull(term, "term");
            windows = List.copyOf(windows);
        }

        /**
         * The reading of a reply that said nothing usable.
         *
         * @param term the term read
         * @return a reading with no window and both flags false
         */
        public static Reading none(final String term) {
            return new Reading(term, List.of(), false, false);
        }
    }

    /**
     * Reads one term in one call.
     *
     * @param subject the term and what the code knows of it; never null
     * @param windows the windows to show, in reading order, numbered from 1; never null
     * @param frame the run's language pair and analysis language; never null
     * @param calls the seam every model call goes through; never null
     * @return the reading; an unreadable reply reads as {@link Reading#none(String)}, and no window means no call and
     *     no reading; or the failed call's error
     */
    public Result<Reading> read(
            final Subject subject, final List<Window> windows, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        logEntry(subject, windows);
        if (windows.isEmpty()) {
            log.debug("Term reading {} has no window: no call", subject.term());
            return Result.ok(Reading.none(subject.term()));
        }
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.TERM_READING,
                List.of(),
                request(subject, windows, frame),
                CallDescriptor.whole(PromptName.TERM_READING));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            log.warn("Term reading {} failed code={}", subject.term(), error.code());
            return Result.err(error);
        }
        return Result.ok(readingOf(Objects.requireNonNull(reply.data(), "reply"), subject, windows, frame));
    }

    private static void logEntry(final Subject subject, final List<Window> windows) {
        log.debug(
                "Term reading {}: {} windows, {} mentions, cluster of {}",
                subject.term(),
                windows.size(),
                subject.mentions(),
                subject.cluster().size());
    }

    private Reading readingOf(
            final ChatResponse reply, final Subject subject, final List<Window> windows, final CallFrame frame) {
        log.trace("Term reading {} reply {}", subject.term(), reply.content());
        final Reading reading =
                TermReadingReplies.read(mapper, reply.content(), subject.term(), windows, frame.analysisLanguage());
        log.debug(
                "Term reading {} answered {} of {} windows, {} votes, speaking={} brand={}",
                subject.term(),
                reading.windows().size(),
                windows.size(),
                reading.windows().stream()
                        .filter(w -> w.vote() != Gender.UNKNOWN)
                        .count(),
                reading.speaking(),
                reading.brand());
        return reading;
    }

    private ChatRequest request(final Subject subject, final List<Window> windows, final CallFrame frame) {
        final List<ChatMessage> messages = List.of(
                new ChatMessage(
                        ChatRole.SYSTEM,
                        templates.renderSystem(PromptName.TERM_READING, frame).strip()),
                new ChatMessage(
                        ChatRole.USER,
                        templates
                                .renderUser(
                                        PromptName.TERM_READING,
                                        Map.of("term", termLine(subject), "windows", numbered(windows)))
                                .strip()));
        log.trace("Term reading messages {}", messages);
        final int expected = BASE_TOKENS + TOKENS_PER_WINDOW * windows.size();
        return ChatRequests.build(
                PromptName.TERM_READING,
                messages,
                new OutputLimit(expected, expected * CAP_FACTOR),
                false,
                frame.requestDefaults());
    }

    // What the code knows, on the term's own line: the count, the tally when there is one, the other spellings.
    private static String termLine(final Subject subject) {
        final StringBuilder line = new StringBuilder(subject.term())
                .append(" — ")
                .append(subject.mentions())
                .append(" mentions");
        final String tally = subject.tally().compact();
        if (!tally.isEmpty()) {
            line.append(" — pronouns after it: ").append(tally);
        }
        if (!subject.cluster().isEmpty()) {
            line.append(" — also written: ").append(String.join(", ", subject.cluster()));
        }
        return line.toString();
    }

    /** The windows as the reading shows them: a number, then the text with its mention marked, a blank line between. */
    static String numbered(final List<Window> windows) {
        final List<String> lines = new ArrayList<>();
        for (int index = 0; index < windows.size(); index++) {
            lines.add("[" + (index + 1) + "] " + marked(windows.get(index)));
        }
        return String.join("\n\n", lines);
    }

    /** The window's text with its mention marked as the reading shows it; the text alone when the mark is off. */
    static String marked(final Window window) {
        final String text = window.text();
        final int start = window.markStart();
        final int end = window.markEnd();
        if (start < 0 || start > end || end > text.length()) {
            return text;
        }
        return text.substring(0, start) + MARK_OPEN + text.substring(start, end) + MARK_CLOSE + text.substring(end);
    }
}
