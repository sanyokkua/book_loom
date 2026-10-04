package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/** The deterministic checks a reply is measured by; no model judges another model here. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplyChecks {

    /** The share of a reply's letters that must be in the target language's script for it to count as translated. */
    private static final double MIN_TARGET_SCRIPT_SHARE = 0.6;

    private static final String DEFAULT_TARGET = "uk";

    /**
     * Whether {@code target} holds exactly the source's tokens in order, no bracket outside a token, and words between
     * every two neighbouring tokens that enclose words in the source — so a pair moved off its words, which the run's
     * gate refuses, fails here too.
     */
    static Check gate(final String masked, final String target) {
        final List<String> tokens = Tokens.inOrder(masked);
        final String rest = Tokens.replace(target, "");
        final boolean clean = Tokens.inOrder(target).equals(tokens) && rest.indexOf('⟦') < 0 && rest.indexOf('⟧') < 0;
        return Check.of(clean && keepsEnclosedWords(masked, target, tokens));
    }

    private static boolean keepsEnclosedWords(final String masked, final String target, final List<String> tokens) {
        return IntStream.range(0, Math.max(0, tokens.size() - 1))
                .filter(index -> !between(masked, tokens.get(index), tokens.get(index + 1))
                        .isBlank())
                .allMatch(index -> !between(target, tokens.get(index), tokens.get(index + 1))
                        .isBlank());
    }

    private static String between(final String text, final String open, final String close) {
        final int start = text.indexOf(open) + open.length();
        final int end = text.indexOf(close, start);
        return end < 0 ? "" : text.substring(start, end);
    }

    /** Whether {@code target} is in the target script, or unchanged where the case asks for a copy. */
    static Check script(final String masked, final String target, final Expect expect) {
        return script(masked, target, expect, DEFAULT_TARGET);
    }

    /** As {@link #script(String, String, Expect)}, for the script of {@code targetTag}'s language. */
    static Check script(final String masked, final String target, final Expect expect, final String targetTag) {
        if (expect.copy()) {
            return Check.of(target.strip().equals(masked.strip()));
        }
        final String letters = Tokens.replace(target, "")
                .codePoints()
                .filter(Character::isLetter)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        final Set<Character.UnicodeScript> expected =
                Languages.scriptOf(targetTag).orElse(Script.UNKNOWN).letterScripts();
        final long inScript = letters.codePoints()
                .filter(cp -> expected.contains(Character.UnicodeScript.of(cp)))
                .count();
        final int total = letters.codePointCount(0, letters.length());
        return Check.of(total > 0 && inScript >= MIN_TARGET_SCRIPT_SHARE * total);
    }

    /** Whether {@code target} matches the case's marker pattern; {@link Check#NA} for a case with none. */
    static Check marker(final String target, final Expect expect) {
        return expect.marker().isEmpty()
                ? Check.NA
                : Check.of(Pattern.compile(expect.marker()).matcher(target).find());
    }

    /** Whether an injection case was translated; {@link Check#NA} for any other case. */
    static Check injection(final String target, final Expect expect) {
        return expect.injection() ? marker(target, expect) : Check.NA;
    }
}
