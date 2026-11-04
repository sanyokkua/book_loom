package ua.bookloom.pipeline.checks;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.qa.CheckLevel;

/**
 * Whether a target paragraph ends with the same class of mark as its source (design D12): most of the times a model
 * drops the end of a paragraph it drops its question mark too. Quotes and closing brackets after the mark are ignored,
 * so a different quotation style is no defect; an ellipsis against a full stop is a difference. A side with no
 * terminal mark (a heading, a line ending in a colon or a dash) and a fragment outside running prose are not judged.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TerminalMarkCheck {

    private static final String CLOSERS = "\"'«»“”„‘’‹›)]}\u00A0 \t\r\n";
    private static final String ASCII_ELLIPSIS = "...";

    /** The class of a paragraph's last mark. */
    private enum Mark {
        QUESTION,
        EXCLAMATION,
        STOP,
        ELLIPSIS
    }

    static Optional<CheckFinding> find(final String source, final String target, final UnitRole role) {
        if (role != UnitRole.BODY) {
            log.debug("Terminal mark check skipped: the unit is {}", role);
            return Optional.empty();
        }
        final Optional<Mark> wanted = lastMark(source);
        final Optional<Mark> written = lastMark(target);
        if (wanted.isEmpty() || written.isEmpty() || wanted.get() == written.get()) {
            return Optional.empty();
        }
        log.debug("Terminal mark check: the target ends with another class of mark than its source");
        log.trace("Terminal mark {} became {}", wanted.get(), written.get());
        final String text = target.stripTrailing();
        return Optional.of(new CheckFinding(
                FindingKind.TERMINAL_MARK,
                new TextSpan(Math.max(0, text.length() - 1), text.length(), tail(text)),
                "The source ends with " + describe(wanted.get()) + " and the translation ends with "
                        + describe(written.get()) + ": end the paragraph with " + describe(wanted.get())
                        + " as the source does.",
                CheckLevel.REPAIR));
    }

    private static Optional<Mark> lastMark(final String text) {
        int end = text.length();
        while (end > 0 && CLOSERS.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        final String body = text.substring(0, end);
        if (body.endsWith(ASCII_ELLIPSIS) || body.endsWith("…")) {
            return Optional.of(Mark.ELLIPSIS);
        }
        return body.isEmpty() ? Optional.empty() : classOf(body);
    }

    // A run such as ?! or !? counts as its strongest mark; a question beats an exclamation.
    private static Optional<Mark> classOf(final String body) {
        final char last = body.charAt(body.length() - 1);
        if (last != '?' && last != '!' && last != '.') {
            return Optional.empty();
        }
        int start = body.length();
        while (start > 0 && "?!.".indexOf(body.charAt(start - 1)) >= 0) {
            start--;
        }
        final String run = body.substring(start);
        if (run.indexOf('?') >= 0) {
            return Optional.of(Mark.QUESTION);
        }
        return Optional.of(run.indexOf('!') >= 0 ? Mark.EXCLAMATION : Mark.STOP);
    }

    private static String tail(final String text) {
        return text.substring(Math.max(0, text.length() - 1));
    }

    private static String describe(final Mark mark) {
        return switch (mark) {
            case QUESTION -> "a question mark";
            case EXCLAMATION -> "an exclamation mark";
            case STOP -> "a full stop";
            case ELLIPSIS -> "an ellipsis";
        };
    }
}
