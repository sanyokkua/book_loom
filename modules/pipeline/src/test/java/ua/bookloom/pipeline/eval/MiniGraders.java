package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniScorer.Verdict;

/**
 * The graders of the Russian-to-Ukrainian mini book. Each reads stored results and the output text only and holds the
 * case author's literal expectation (an alphabet, a word list, a spelling); none calls a production check, so a grader
 * never agrees with the code under test by sharing its mistake.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MiniGraders {

    private static final Map<String, String> ALPHABETS = Map.of(
            "uk", "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя",
            "en", "abcdefghijklmnopqrstuvwxyz");
    private static final String DASHES = "—–―";
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final String FORBIDDEN = "!";

    static boolean isGlossaryLevel(final CheckKind check) {
        return check == CheckKind.GLOSSARY_ONE_ENTRY || check == CheckKind.GLOSSARY_GENDER;
    }

    static boolean isRunLevel(final CheckKind check) {
        return isGlossaryLevel(check)
                || check == CheckKind.WINDOW_INJECTION
                || check == CheckKind.PROOFREAD_NEVER_WORSE;
    }

    /**
     * Grades the recorded translate-family calls of a run: map-block entries must occur in their call's window, and
     * block-8 names must already be said by the book at the window's end.
     *
     * @param calls the run's recorded calls; other families are skipped
     * @param bookTexts the book's block texts in reading order
     * @param gold the gold map, which gives the forms of each name
     * @return the verdict, whose detail counts what was checked and names what lies outside
     */
    static Verdict windowInjection(
            final List<CaseRecord.Exchange> calls, final List<String> bookTexts, final List<MiniGold.Entry> gold) {
        return WindowInjectionGrader.grade(calls, bookTexts, gold);
    }

    /**
     * Grades the Proofread stage's decisions of a run: a change whose verdict is KEPT must not have more failing checks
     * on the kept text than on the text before it. Task 13.2 adds the stored verdict; it maps each one to a
     * {@link ProofreadChange}.
     *
     * @param changes what the stage decided, one entry per segment it read
     * @return the verdict, whose detail names each kept change that got worse
     */
    static Verdict proofreadNeverWorse(final List<ProofreadChange> changes) {
        Objects.requireNonNull(changes, "changes");
        final List<String> worse = changes.stream()
                .filter(ProofreadChange::isWorse)
                .map(ProofreadChange::segmentId)
                .toList();
        log.debug("proofread never-worse grader changes={} worse={}", changes.size(), worse);
        return worse.isEmpty() ? Verdict.pass("changes " + changes.size()) : Verdict.fail("kept but worse " + worse);
    }

    static Verdict targetAlphabet(final String target, final List<String> args) {
        final String alphabet = args.isEmpty() ? null : ALPHABETS.get(args.get(0));
        if (alphabet == null) {
            return Verdict.fail("no alphabet for " + args);
        }
        final List<String> outside = target.codePoints()
                .filter(Character::isLetter)
                .mapToObj(cp -> new String(Character.toChars(Character.toLowerCase(cp))))
                .filter(letter -> !alphabet.contains(letter))
                .distinct()
                .toList();
        return outside.isEmpty()
                ? Verdict.pass("alphabet " + args.get(0))
                : Verdict.fail("letters outside: " + outside);
    }

    static Verdict foreignScript(final String target) {
        final List<String> foreign = target.codePoints()
                .filter(Character::isLetter)
                .filter(cp -> !isCyrillicOrLatin(cp))
                .mapToObj(cp -> new String(Character.toChars(cp)))
                .distinct()
                .toList();
        return foreign.isEmpty() ? Verdict.pass("none") : Verdict.fail("foreign script " + foreign);
    }

    private static boolean isCyrillicOrLatin(final int codePoint) {
        final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.LATIN;
    }

    static Verdict noSourceLine(final String source, final String target) {
        return plain(source).equals(plain(target))
                ? Verdict.fail("the target is the source line")
                : Verdict.pass("differs");
    }

    private static String plain(final String text) {
        return NOT_LETTER_OR_DIGIT.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    static Verdict sourceOnlyWords(final String target, final List<String> words) {
        final List<String> found =
                words.stream().filter(w -> containsWord(target, w)).toList();
        return found.isEmpty() ? Verdict.pass("none of " + words) : Verdict.fail("source-only words " + found);
    }

    static Verdict dialogueDash(final String source, final String target) {
        final boolean sourceDash = startsWithDash(source);
        return !sourceDash || startsWithDash(target)
                ? Verdict.pass(sourceDash ? "dash kept" : "no dash in the source")
                : Verdict.fail("the opening dash is lost");
    }

    private static boolean startsWithDash(final String text) {
        final String stripped = text.strip();
        return !stripped.isEmpty() && DASHES.indexOf(stripped.charAt(0)) >= 0;
    }

    static Verdict vocative(final String target, final List<String> forms) {
        return forms.stream().anyMatch(f -> containsWord(target, f))
                ? Verdict.pass("vocative present")
                : Verdict.fail("none of " + forms);
    }

    static Verdict narratorGender(final String target, final List<String> forms) {
        final List<String> wanted =
                forms.stream().filter(f -> !f.startsWith(FORBIDDEN)).toList();
        final List<String> forbidden = forms.stream()
                .filter(f -> f.startsWith(FORBIDDEN))
                .map(f -> f.substring(FORBIDDEN.length()))
                .filter(f -> containsWord(target, f))
                .toList();
        if (!forbidden.isEmpty()) {
            return Verdict.fail("wrong-gender forms " + forbidden);
        }
        return wanted.isEmpty() || wanted.stream().anyMatch(f -> containsWord(target, f))
                ? Verdict.pass("narrator form agrees")
                : Verdict.fail("none of " + wanted);
    }

    static Verdict oneSpelling(final String targets, final List<String> spellings) {
        final String lower = targets.toLowerCase(Locale.ROOT);
        final List<String> seen = spellings.stream()
                .filter(s -> lower.contains(s.toLowerCase(Locale.ROOT)))
                .toList();
        return seen.size() <= 1 ? Verdict.pass("spellings " + seen) : Verdict.fail("spelled both " + seen);
    }

    static Verdict glossary(final Expectation expectation, final List<GlossaryEntry> glossary) {
        Objects.requireNonNull(expectation, "expectation");
        final boolean byGender = expectation.check() == CheckKind.GLOSSARY_GENDER;
        final List<String> args = expectation.args();
        final List<String> forms = byGender ? args.subList(0, args.size() - 1) : args;
        final List<GlossaryEntry> entries =
                glossary.stream().filter(e -> isFiledUnder(e, forms)).toList();
        log.debug("glossary grader {} forms={} entries={}", expectation.id(), forms, entries.size());
        return byGender ? gender(entries, forms, args.get(args.size() - 1)) : oneEntry(entries, forms);
    }

    private static boolean isFiledUnder(final GlossaryEntry entry, final List<String> forms) {
        return forms.contains(entry.term()) || entry.forms().stream().anyMatch(forms::contains);
    }

    private static Verdict oneEntry(final List<GlossaryEntry> entries, final List<String> forms) {
        return entries.size() == 1
                ? Verdict.pass("one entry for " + forms)
                : Verdict.fail(entries.size() + " entries for " + forms);
    }

    private static Verdict gender(final List<GlossaryEntry> entries, final List<String> forms, final String wanted) {
        final Gender gender = Gender.valueOf(wanted);
        if (entries.isEmpty()) {
            return Verdict.fail("no entry for " + forms);
        }
        final List<String> wrong = entries.stream()
                .filter(e -> e.gender() != gender)
                .map(e -> e.term() + "=" + e.gender())
                .toList();
        return wrong.isEmpty() ? Verdict.pass("gender " + gender) : Verdict.fail("wrong gender " + wrong);
    }

    private static boolean containsWord(final String text, final String word) {
        return Pattern.compile(
                        "(?<![\\p{L}])" + Pattern.quote(word) + "(?![\\p{L}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text)
                .find();
    }
}
