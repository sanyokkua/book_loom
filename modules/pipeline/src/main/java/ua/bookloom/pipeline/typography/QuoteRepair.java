package ua.bookloom.pipeline.typography;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.QuoteConventions;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * The deterministic repair of a target's quote marks, used only after the quote-balance check blocked it. It changes
 * quote marks and nothing else, and it either returns a text whose marks pair up under the language's table or the
 * input untouched: collapses a doubled mark, writes straight marks as the language's pairs, replaces a closer that
 * crosses the open mark with the open mark's own closer, opens a paragraph whose first mark is a stray closer when the
 * source opens with a quote, drops a stray closer at a paragraph edge, and closes one unclosed opener before the
 * dash clause that follows it or at the paragraph's end, before a single final full stop. It leaves a text alone when its source is itself open, when
 * a placeholder token sits inside a word, or when any rule above would be a guess. A repaired text repairs to itself.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuoteRepair {

    private static final Pattern MID_WORD_TOKEN = Pattern.compile("\\p{L}⟦g\\d+⟧\\p{L}");
    private static final Pattern DASH_CLAUSE = Pattern.compile("(,)?[\\s\\u00A0]+[—–][\\s\\u00A0]");
    private static final String SOURCE_OPENERS = "\"«„“‹";
    private static final char STRAIGHT = '"';

    /**
     * What a repair did.
     *
     * @param text the text after the repair; the input itself when nothing was repaired
     * @param marks how many quote marks were collapsed, restyled, replaced, added or dropped
     */
    public record Repaired(String text, int marks) {

        /** Rejects a missing text. */
        public Repaired {
            Objects.requireNonNull(text, "text");
        }

        /**
         * Whether the repair changed the text.
         *
         * @return {@code true} when at least one mark changed
         */
        public boolean isChanged() {
            return marks > 0;
        }

        /**
         * The note a review shows beside a repaired segment.
         *
         * @return the note with the count; empty when nothing changed
         */
        public String note() {
            return isChanged() ? "Quote marks repaired: " + marks + "." : "";
        }
    }

    /**
     * Repairs one target's quote marks.
     *
     * @param maskedSource the segment's masked source, read only to tell whether its own quotes are balanced and how
     *     it begins
     * @param maskedTarget the candidate with its {@code ⟦gN⟧} tokens in place
     * @param sourceLanguage the source language tag, or {@code null} when none is declared
     * @param targetLanguage the BCP 47 tag of the target; a language with no quote line of its own is left alone
     * @return the repaired text and how many marks changed; the input and zero when it is already balanced, cannot be
     *     repaired with certainty, or its source is itself open
     */
    public static Repaired repair(
            final String maskedSource,
            final String maskedTarget,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedTarget, "maskedTarget");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final Repaired unchanged = new Repaired(maskedTarget, 0);
        final Optional<List<QuotePair>> pairs = QuoteConventions.ownLine(targetLanguage);
        if (pairs.isEmpty()
                || QuoteConventions.isBalanced(maskedTarget, targetLanguage)
                || !QuoteConventions.isBalanced(maskedSource, sourceLanguage)
                || MID_WORD_TOKEN.matcher(maskedTarget).find()) {
            log.debug("Quote repair skipped: nothing to repair or not certain enough");
            return unchanged;
        }
        final Optional<Repaired> repaired = attempt(maskedSource, maskedTarget, pairs.get());
        if (repaired.isEmpty()
                || !QuoteConventions.isBalanced(repaired.get().text(), targetLanguage)
                || !sameWords(maskedTarget, repaired.get().text())) {
            log.debug("Quote repair gave up: no certain repair");
            return unchanged;
        }
        log.debug("Quote repair changed {} mark(s)", repaired.get().marks());
        return repaired.get();
    }

    private static Optional<Repaired> attempt(final String source, final String target, final List<QuotePair> pairs) {
        final String collapsed = collapseDoubled(target, pairs);
        final Edit styled = QuoteStyler.apply(collapsed, pairs);
        final int collapsedMarks = target.length() - collapsed.length();
        return new Fixer(styled.text(), pairs, opensWithQuote(source))
                .run()
                .map(fixed -> new Repaired(fixed.text(), collapsedMarks + styled.count() + fixed.count()));
    }

    private static String collapseDoubled(final String text, final List<QuotePair> pairs) {
        String result = text;
        for (final QuotePair pair : pairs) {
            for (final char mark : new char[] {pair.open(), pair.close()}) {
                result = result.replaceAll("(" + Pattern.quote(String.valueOf(mark)) + ")\\1+", "$1");
            }
        }
        return result;
    }

    private static boolean opensWithQuote(final String source) {
        final int first = firstVisible(source);
        return first < source.length() && SOURCE_OPENERS.indexOf(source.charAt(first)) >= 0;
    }

    // The first character that is neither whitespace nor part of a token.
    private static int firstVisible(final String text) {
        final Matcher tokens = Tokens.matcher(text);
        int at = 0;
        while (at < text.length()) {
            if (Character.isWhitespace(text.charAt(at))) {
                at++;
            } else if (tokens.find(at) && tokens.start() == at) {
                at = tokens.end();
            } else {
                return at;
            }
        }
        return at;
    }

    private static boolean sameWords(final String before, final String after) {
        return withoutMarks(before).equals(withoutMarks(after));
    }

    private static String withoutMarks(final String text) {
        return text.replaceAll("[«»„“”‹›\"]", "");
    }

    private static boolean isQuoteMark(final List<QuotePair> pairs, final char c) {
        return c == STRAIGHT || pairs.stream().anyMatch(pair -> pair.open() == c || pair.close() == c);
    }

    /** One left-to-right scan that mirrors the balance check and fixes the defects it can fix with certainty. */
    private static final class Fixer {

        private record Open(QuotePair pair, int at) {}

        private final String text;
        private final List<QuotePair> pairs;
        private final boolean sourceOpensWithQuote;
        private final StringBuilder out = new StringBuilder();
        private final Deque<Open> stack = new ArrayDeque<>();
        private int changed;

        Fixer(final String text, final List<QuotePair> pairs, final boolean sourceOpensWithQuote) {
            this.text = text;
            this.pairs = pairs;
            this.sourceOpensWithQuote = sourceOpensWithQuote;
        }

        Optional<Edit> run() {
            for (int i = 0; i < text.length(); i++) {
                if (!step(text.charAt(i), i)) {
                    return Optional.empty();
                }
            }
            return closeUnclosed() ? Optional.of(new Edit(out.toString(), changed)) : Optional.empty();
        }

        private boolean step(final char c, final int index) {
            final boolean closesTop = !stack.isEmpty() && stack.peek().pair().close() == c;
            final Optional<QuotePair> opened =
                    pairs.stream().filter(pair -> pair.open() == c).findFirst();
            if (!closesTop && opened.isEmpty() && pairs.stream().anyMatch(pair -> pair.close() == c)) {
                return mended(c, index);
            }
            if (closesTop) {
                stack.pop();
            } else if (opened.isPresent()) {
                stack.push(new Open(opened.get(), out.length()));
            }
            out.append(c);
            return true;
        }

        // A closer that closes nothing: crossed with the open mark, or stray.
        private boolean mended(final char c, final int index) {
            if (!stack.isEmpty()) {
                out.append(stack.pop().pair().close());
                changed++;
                return true;
            }
            if (isAtEdge(index)) {
                changed++;
                return true;
            }
            if (sourceOpensWithQuote && noMarkBefore()) {
                out.insert(firstVisible(out.toString()), pairs.getFirst().open());
                out.append(c);
                changed++;
                return true;
            }
            return false;
        }

        private boolean noMarkBefore() {
            return out.chars().noneMatch(mark -> isQuoteMark(pairs, (char) mark));
        }

        private boolean isAtEdge(final int index) {
            return firstVisible(text) == index || firstVisible(text.substring(index + 1)) == text.length() - index - 1;
        }

        private boolean closeUnclosed() {
            if (stack.isEmpty()) {
                return true;
            }
            if (stack.size() > 1) {
                return false;
            }
            final Open open = stack.pop();
            out.insert(closingPoint(open.at()), open.pair().close());
            changed++;
            return true;
        }

        private int closingPoint(final int openedAt) {
            final String rest = out.substring(openedAt + 1);
            final Matcher dash = DASH_CLAUSE.matcher(rest);
            if (dash.find() && noMarkIn(rest.substring(0, dash.start()))) {
                return openedAt + 1 + dash.start();
            }
            int end = out.length();
            while (end > 0 && endsWithSpaceOrToken(end)) {
                end = newEnd(end);
            }
            return isSingleFinalStop(end) ? end - 1 : end;
        }

        // The language's convention keeps a closing full stop outside the mark («…загубився».), so the mark goes before
        // it.
        private boolean isSingleFinalStop(final int end) {
            return end > 1 && out.charAt(end - 1) == '.' && out.charAt(end - 2) != '.';
        }

        private boolean noMarkIn(final String part) {
            return part.chars().noneMatch(mark -> isQuoteMark(pairs, (char) mark));
        }

        private boolean endsWithSpaceOrToken(final int end) {
            return Character.isWhitespace(out.charAt(end - 1)) || out.charAt(end - 1) == '⟧';
        }

        private int newEnd(final int end) {
            if (out.charAt(end - 1) != '⟧') {
                return end - 1;
            }
            final int start = out.lastIndexOf("⟦g", end - 1);
            return start < 0 ? 0 : start;
        }
    }
}
