package ua.bookloom.pipeline.checks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Whether the target's quote marks pair up — every opening mark closed by its own closing mark, none closed that was
 * never opened — when the source's marks do. A source that is itself open (a quotation running on into the next
 * paragraph) lets the target be open too, so a segment is only blamed for what the translator changed. Only the quote
 * marks of each language's table count; a dash, a comma or any other punctuation is never read, since what is right
 * differs by language.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuoteBalanceCheck {

    private static final int CONTEXT = 12;

    private enum Problem {
        UNCLOSED("The quote opened here is never closed. Close it with the matching mark."),
        STRAY_CLOSE("A closing quote mark has no opening mark before it. Remove it or open the quote."),
        CROSSED("A closing quote mark does not match the mark that was opened. Use the matching one.");

        private final String explanation;

        Problem(final String explanation) {
            this.explanation = explanation;
        }
    }

    private record Defect(int index, Problem problem) {}

    static Optional<CheckFinding> find(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        final Optional<Defect> sourceDefect = defect(source, QuoteConventions.forLanguage(sourceLanguage));
        final Optional<Defect> targetDefect = defect(target, QuoteConventions.forLanguage(targetLanguage));
        if (targetDefect.isEmpty()) {
            return Optional.empty();
        }
        log.debug(
                "Quote defect {} in the target, source defect {}",
                targetDefect.get().problem(),
                sourceDefect.map(Defect::problem));
        if (sourceDefect.isPresent()) {
            return Optional.empty();
        }
        final List<QuotePair> targetPairs = QuoteConventions.forLanguage(targetLanguage);
        if (defect(target, QuoteConventions.withEnglish(targetPairs)).isEmpty()) {
            return Optional.of(englishQuotes(target, targetPairs));
        }
        final Defect found = targetDefect.get();
        final int from = Math.max(0, found.problem() == Problem.UNCLOSED ? found.index() : found.index() - CONTEXT);
        final int to = Math.min(
                target.length(), found.problem() == Problem.UNCLOSED ? found.index() + CONTEXT : found.index() + 1);
        return Optional.of(new CheckFinding(
                FindingKind.UNBALANCED_QUOTES,
                new TextSpan(from, to, target.substring(from, to)),
                found.problem().explanation + " The source's quotes are balanced.",
                true));
    }

    // Balanced once English curly quotes count as a pair: a style note for review, not a reason to repair.
    private static CheckFinding englishQuotes(final String target, final List<QuotePair> pairs) {
        final int at = IntStream.range(0, target.length())
                .filter(index -> isEnglishMark(pairs, target.charAt(index)))
                .findFirst()
                .orElse(0);
        return new CheckFinding(
                FindingKind.UNBALANCED_QUOTES,
                new TextSpan(at, at + 1, target.substring(at, at + 1)),
                "The target uses English quote marks, which are balanced; the language's own marks are "
                        + pairs.getFirst().open() + pairs.getFirst().close() + ".",
                false);
    }

    private static boolean isEnglishMark(final List<QuotePair> pairs, final char mark) {
        return (mark == '“' || mark == '”') && pairs.stream().noneMatch(pair -> pair.open() == mark);
    }

    private static Optional<Defect> defect(final String text, final List<QuotePair> pairs) {
        final Deque<Integer> open = new ArrayDeque<>();
        for (int index = 0; index < text.length(); index++) {
            final char mark = text.charAt(index);
            final Optional<Defect> problem = step(text, pairs, open, index, mark);
            if (problem.isPresent()) {
                return problem;
            }
        }
        return open.isEmpty() ? Optional.empty() : Optional.of(new Defect(open.peek(), Problem.UNCLOSED));
    }

    private static Optional<Defect> step(
            final String text,
            final List<QuotePair> pairs,
            final Deque<Integer> open,
            final int index,
            final char mark) {
        if (!open.isEmpty() && closerOf(pairs, text.charAt(open.peek())) == mark) {
            open.pop();
            return Optional.empty();
        }
        if (pairs.stream().anyMatch(pair -> pair.open() == mark)) {
            open.push(index);
            return Optional.empty();
        }
        if (pairs.stream().anyMatch(pair -> pair.close() == mark)) {
            return Optional.of(new Defect(index, open.isEmpty() ? Problem.STRAY_CLOSE : Problem.CROSSED));
        }
        return Optional.empty();
    }

    private static char closerOf(final List<QuotePair> pairs, final char open) {
        return pairs.stream()
                .filter(pair -> pair.open() == open)
                .findFirst()
                .map(QuotePair::close)
                .orElse(open);
    }
}
