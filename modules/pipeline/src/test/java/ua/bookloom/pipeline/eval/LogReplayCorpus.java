package ua.bookloom.pipeline.eval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The model calls and segment decisions of a trace log ({@code bookloom-trace.log}, plain or {@code .gz}), read for a
 * replay: a {@code Model call going out} line, the next {@code HTTP request body} and {@code HTTP response body} lines
 * of the same thread make one {@link LoggedCall}, and the {@code Decided segmentId=} lines say how each segment ended.
 * The calls hold book text, so they stay in memory (or under {@code build/}); nothing here writes them out.
 *
 * @param calls the calls in log order
 * @param decisions how each segment ended, by segment id; the last decision of a segment wins
 */
@Slf4j
record LogReplayCorpus(List<LoggedCall> calls, Map<String, Decision> decisions) {

    /** How a segment ended in the run that wrote the log. */
    enum Outcome {
        /** Kept as drafted. */
        ACCEPTED,
        /** Accepted after a repair. */
        REPAIRED,
        /** Left flagged for the person. */
        FLAGGED,
        /** The log holds no decision for it. */
        UNKNOWN
    }

    /**
     * A segment's final decision.
     *
     * @param status the status word of the log, {@code ACCEPTED} or {@code FLAGGED}
     * @param path how it was reached, {@code DRAFT}, {@code REPAIRED}, {@code TM_REUSE} …
     */
    record Decision(String status, String path) {

        Outcome outcome() {
            if ("FLAGGED".equals(status)) {
                return Outcome.FLAGGED;
            }
            return "REPAIRED".equals(path) ? Outcome.REPAIRED : Outcome.ACCEPTED;
        }
    }

    private static final Pattern LINE = Pattern.compile(
            "^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3} \\w+ \\[job=\\S* segment=\\S*\\] \\[([^\\]]+)\\] \\S+ - (.*)$");
    private static final Pattern GOING_OUT =
            Pattern.compile("^Model call going out kind=(\\w+) segmentIds=\\[(.*)\\]$");
    private static final Pattern DECIDED =
            Pattern.compile("^Decided segmentId=(\\S+) status=(\\w+) reason=\\S+ path=(\\w+) rounds=\\d+$");
    private static final String REQUEST = "HTTP request body host=";
    private static final String RESPONSE = "HTTP response body host=";
    private static final String BODY = " body=";
    private static final String NEWLINE_MARK = " ⏎ ";

    /** Copies the lists. */
    LogReplayCorpus {
        calls = List.copyOf(calls);
        decisions = Map.copyOf(decisions);
    }

    /** How the segments of a call ended: the worst outcome among them, or unknown when none has a decision. */
    Outcome outcomeOf(final LoggedCall call) {
        Outcome worst = Outcome.UNKNOWN;
        for (final String id : call.segmentIds()) {
            final Decision decision = decisions.get(id);
            if (decision != null) {
                worst = worse(worst, decision.outcome());
            }
        }
        return worst;
    }

    private static Outcome worse(final Outcome known, final Outcome other) {
        final List<Outcome> order = List.of(Outcome.UNKNOWN, Outcome.ACCEPTED, Outcome.REPAIRED, Outcome.FLAGGED);
        return order.indexOf(other) > order.indexOf(known) ? other : known;
    }

    /**
     * Reads a trace log.
     *
     * @param file the log file; a name ending in {@code .gz} is read as gzip
     * @param maxBytes the most bytes of text to read (the first ones), or zero for the whole log
     * @return the calls and decisions found; empty when the file holds none
     */
    static LogReplayCorpus load(final Path file, final long maxBytes) {
        Objects.requireNonNull(file, "file");
        try (InputStream raw = Files.newInputStream(file);
                InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            final LogReplayCorpus corpus = read(reader, maxBytes);
            log.info(
                    "Replay log read calls={} decisions={}",
                    corpus.calls().size(),
                    corpus.decisions().size());
            return corpus;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LogReplayCorpus read(final BufferedReader reader, final long maxBytes) throws IOException {
        final Reading reading = new Reading();
        long read = 0;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            reading.accept(line);
            read += line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (maxBytes > 0 && read >= maxBytes) {
                break;
            }
        }
        return reading.finish();
    }

    /** The state of one read: the call each thread is in the middle of, the calls done and the decisions so far. */
    private static final class Reading {

        private final Map<String, Pending> open = new HashMap<>();
        private final List<LoggedCall> done = new ArrayList<>();
        private final Map<String, Decision> decisions = new HashMap<>();

        void accept(final String line) {
            final Matcher matcher = LINE.matcher(line);
            if (!matcher.matches()) {
                return;
            }
            final String thread = matcher.group(1);
            final String message = matcher.group(2);
            final Matcher out = GOING_OUT.matcher(message);
            final Matcher decided = DECIDED.matcher(message);
            if (out.matches()) {
                close(thread);
                open.put(thread, new Pending(CallKind.valueOf(out.group(1)), ids(out.group(2))));
            } else if (decided.matches()) {
                decisions.put(decided.group(1), new Decision(decided.group(2), decided.group(3)));
            } else if (message.startsWith(REQUEST)) {
                final Pending pending = open.get(thread);
                if (pending != null && pending.request == null) {
                    pending.request = bodyOf(message);
                }
            } else if (message.startsWith(RESPONSE)) {
                final Pending pending = open.get(thread);
                if (pending != null && pending.request != null) {
                    pending.response = bodyOf(message).replace(NEWLINE_MARK, "\n");
                    close(thread);
                }
            }
        }

        LogReplayCorpus finish() {
            new ArrayList<>(open.keySet()).forEach(this::close);
            done.sort((a, b) -> Integer.compare(a.order(), b.order()));
            return new LogReplayCorpus(done, decisions);
        }

        private void close(final String thread) {
            final Pending pending = open.remove(thread);
            if (pending != null && pending.request != null) {
                done.add(new LoggedCall(pending.number, pending.kind, pending.ids, pending.request, pending.response));
            }
        }

        private static String bodyOf(final String message) {
            return message.substring(message.indexOf(BODY) + BODY.length());
        }

        private static List<String> ids(final String list) {
            return list.isBlank()
                    ? List.of()
                    : Arrays.stream(list.split(",\\s*")).toList();
        }

        private int counter;

        private final class Pending {
            final int number = counter++;
            final CallKind kind;
            final List<String> ids;

            @Nullable
            String request;

            @Nullable
            String response;

            Pending(final CallKind kind, final List<String> ids) {
                this.kind = kind;
                this.ids = ids;
            }
        }
    }

    /** The calls of the kinds given, in log order. */
    List<LoggedCall> calls(final CallKind... kinds) {
        final List<CallKind> wanted = List.of(kinds);
        return calls.stream().filter(call -> wanted.contains(call.kind())).toList();
    }
}
