package ua.bookloom.pipeline.eval;

import java.util.Set;
import java.util.regex.Pattern;
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
     * Whether a suggested name is in the target script, or unchanged where the case asks for a copy. A draft reply is
     * judged by the run's own checks ({@link ReplyJudge}); a name suggestion is no draft, so it has only this.
     */
    static Check script(final String masked, final String target, final Expect expect) {
        if (expect.copy()) {
            return Check.of(target.strip().equals(masked.strip()));
        }
        final String letters = Tokens.replace(target, "")
                .codePoints()
                .filter(Character::isLetter)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        final Set<Character.UnicodeScript> expected =
                Languages.scriptOf(DEFAULT_TARGET).orElse(Script.UNKNOWN).letterScripts();
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
