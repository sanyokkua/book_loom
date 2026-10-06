package ua.bookloom.pipeline.eval;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 * Reads what the run only reports in its log — why a batch item fell back to its own draft, which reviewer edits the
 * verifier refused, which segments failed a hard gate in their first round and on what — by listening on the
 * {@code ua.bookloom} logger for the length of the run, with that logger at DEBUG whatever the configuration says. Test
 * code only: the application stays on the SLF4J facade.
 */
final class SequenceLogCapture implements AutoCloseable {

    private static final Pattern FALLBACK =
            Pattern.compile("Batch item falls back to its own draft segmentId=(\\S+) status=(\\S+) problems=(.*)");
    private static final Pattern FIRST_EVALUATION =
            Pattern.compile("Evaluated segment=(\\S+) round=0 .*hardGatesPass=false.*");
    private static final Pattern FIRST_REPAIR =
            Pattern.compile("Round choice segment=(\\S+) round=1 kind=directed-fix findingKinds=\\[(.*)]");
    private static final Pattern COMMA = Pattern.compile(",\\s*");
    private static final String REFUSED = "Edit refused";

    private final Logger logger = (Logger) LoggerFactory.getLogger("ua.bookloom");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level before = logger.getLevel();

    SequenceLogCapture() {
        logger.setLevel(Level.DEBUG);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    /** The batch fallbacks of the given segments, counted per {@code status/problems}. */
    Map<String, Integer> fallbackReasons(final Set<String> segmentIds) {
        final Map<String, Integer> reasons = new LinkedHashMap<>();
        for (final ILoggingEvent event : snapshot()) {
            final Matcher match = FALLBACK.matcher(event.getFormattedMessage());
            if (match.matches() && segmentIds.contains(match.group(1))) {
                reasons.merge(match.group(2) + "/" + match.group(3), 1, Integer::sum);
            }
        }
        return reasons;
    }

    /** The segments among the given ones whose first evaluation failed a hard gate. */
    Set<String> hardGateFailuresRound0(final Set<String> segmentIds) {
        final Set<String> failed = new LinkedHashSet<>();
        for (final ILoggingEvent event : snapshot()) {
            final Matcher match = FIRST_EVALUATION.matcher(event.getFormattedMessage());
            if (match.matches() && segmentIds.contains(match.group(1))) {
                failed.add(match.group(1));
            }
        }
        return failed;
    }

    /** The findings that sent the given segments to their first repair round, counted per finding kind. */
    Map<String, Integer> firstRepairKinds(final Set<String> segmentIds) {
        final Map<String, Integer> kinds = new LinkedHashMap<>();
        for (final ILoggingEvent event : snapshot()) {
            final Matcher match = FIRST_REPAIR.matcher(event.getFormattedMessage());
            if (match.matches() && segmentIds.contains(match.group(1))) {
                COMMA.splitAsStream(match.group(2)).forEach(kind -> kinds.merge(kind, 1, Integer::sum));
            }
        }
        return kinds;
    }

    /** The refused edits of the given segments. */
    int editsRefused(final Set<String> segmentIds) {
        return (int) snapshot().stream()
                .filter(event -> event.getFormattedMessage().startsWith(REFUSED))
                .filter(event -> segmentIds.contains(event.getMDCPropertyMap().get("segment")))
                .count();
    }

    private List<ILoggingEvent> snapshot() {
        return new ArrayList<>(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(before);
        appender.stop();
    }
}
