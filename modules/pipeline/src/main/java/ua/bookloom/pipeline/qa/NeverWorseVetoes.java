package ua.bookloom.pipeline.qa;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.LanguageIdentityCheck;
import ua.bookloom.pipeline.checks.NegationCount;
import ua.bookloom.pipeline.checks.Sentences;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.util.text.StressMarks;

/**
 * The never-worse vetoes in their fixed order (design D14): the first one a candidate breaks names the rejection, so
 * a reason is stable for one pair of texts. Every veto compares the candidate with the current text and the source,
 * never with an ideal, so a defect both texts share never refuses a change; a veto that counts what a translation must
 * keep (sentences, negations, numbers, quotes, dashes, clause marks) fires only when the candidate loses what the
 * source holds, so a repair that removes a wrong number, a stray quote or a doubled sentence is not refused for it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NeverWorseVetoes {

    static final String PLACEHOLDER = "placeholder";
    static final String NEW_BLOCK = "new-block:";
    static final String COUNT = "count:";

    /** The checks whose finding count may not rise in a kept candidate, even when it clears the marking finding. */
    private static final List<String> COUNTED = Stream.of(
                    CheckName.ALPHABET,
                    CheckName.LANGUAGE_IDENTITY,
                    CheckName.FUNCTION_WORD_RESIDUE,
                    CheckName.SCRIPT_PURITY,
                    CheckName.NUMBER,
                    CheckName.TERMINAL_MARK,
                    CheckName.ADDRESS_PARITY,
                    CheckName.SENTENCE_COUNT)
            .map(CheckName::raisedBy)
            .toList();

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** Both texts of one comparison, read once. */
    private record Pair(String old, String fresh, String source, WindowFacts facts) {}

    /** One text veto: its stable name and the test a candidate breaks it by. */
    private record Rule(String name, Predicate<Pair> breaks) {}

    private static final List<Rule> TEXT_RULES = List.of(
            new Rule("forbidden-letter", pair -> rises(pair, text -> forbiddenLetters(text, pair.facts()))),
            new Rule(
                    CheckName.FUNCTION_WORD_RESIDUE.raisedBy(),
                    pair -> rises(pair, text -> residue(text, pair.facts()))),
            new Rule("name-form", pair -> rises(pair, text -> NameVetoes.foreignForms(text, pair.facts()))),
            new Rule(
                    "name-dropped",
                    pair -> NameVetoes.dropsName(pair.old(), pair.fresh(), pair.source(), pair.facts())),
            new Rule("sentences", pair -> falls(pair, text -> (long) Sentences.countLenient(text))),
            new Rule("negation", NeverWorseVetoes::losesNegation),
            new Rule("number-lost", pair -> losesNumber(pair.old(), pair.fresh(), pair.source())),
            new Rule("quotes", pair -> falls(pair, text -> TextGuards.count(text, TextGuards.QUOTE_MARKS))),
            new Rule("dashes", pair -> falls(pair, text -> TextGuards.count(text, TextGuards.DASHES))),
            new Rule("clauses", pair -> falls(pair, text -> TextGuards.count(text, TextGuards.CLAUSE_MARKS))),
            new Rule("latin-run", pair -> TextGuards.bringsLatinRun(pair.old(), pair.fresh())),
            new Rule("stress-mark", pair -> rises(pair, text -> (long) StressMarks.count(text))));

    /**
     * The first veto the candidate breaks.
     *
     * @param current the non-null current text
     * @param candidate the non-null candidate text
     * @param facts the window's facts
     * @return the veto's stable name if one is broken, or empty if none is
     */
    static Optional<String> first(final Candidate current, final Candidate candidate, final WindowFacts facts) {
        final Pair pair = new Pair(
                DisplayText.of(current.masked()),
                DisplayText.of(candidate.masked()),
                DisplayText.of(facts.sourceMasked()),
                facts);
        final Optional<String> broken = failsPlaceholder(candidate)
                .or(() -> newBlock(current.qa(), candidate.qa()))
                .or(() -> TEXT_RULES.stream()
                        .filter(rule -> rule.breaks().test(pair))
                        .map(Rule::name)
                        .findFirst())
                .or(() -> countRise(current.qa(), candidate.qa()));
        log.debug("Never-worse vetoes broken={}", broken.orElse("none"));
        return broken;
    }

    private static Optional<String> failsPlaceholder(final Candidate candidate) {
        return candidate.qa().hardGates().stream()
                        .anyMatch(gate -> gate.check() == CheckName.PLACEHOLDER && !gate.passed())
                ? Optional.of(PLACEHOLDER)
                : Optional.empty();
    }

    private static Optional<String> newBlock(final QaResult current, final QaResult candidate) {
        final Set<String> before = blocking(current);
        return blocking(candidate).stream()
                .filter(name -> !before.contains(name))
                .findFirst()
                .map(name -> NEW_BLOCK + name);
    }

    private static Set<String> blocking(final QaResult qa) {
        return qa.findings().stream()
                .filter(finding -> CheckLevels.of(finding) == CheckLevel.BLOCK)
                .map(QaFinding::raisedBy)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Optional<String> countRise(final QaResult current, final QaResult candidate) {
        final Map<String, Long> before = counts(current);
        final Map<String, Long> after = counts(candidate);
        return COUNTED.stream()
                .filter(name -> after.getOrDefault(name, 0L) > before.getOrDefault(name, 0L))
                .findFirst()
                .map(name -> COUNT + name);
    }

    private static Map<String, Long> counts(final QaResult qa) {
        return qa.findings().stream().collect(Collectors.groupingBy(QaFinding::raisedBy, Collectors.counting()));
    }

    private static boolean rises(final Pair pair, final ToLongFunction<String> measure) {
        final long before = measure.applyAsLong(pair.old());
        final long after = measure.applyAsLong(pair.fresh());
        log.trace("Never-worse measure {}->{}", before, after);
        return after > before;
    }

    // The candidate falls below both the current text and the source: what it lost, the source held.
    private static boolean falls(final Pair pair, final ToLongFunction<String> measure) {
        return fallsBelow(pair, measure, measure.applyAsLong(pair.source()));
    }

    private static boolean fallsBelow(final Pair pair, final ToLongFunction<String> measure, final long source) {
        final long before = measure.applyAsLong(pair.old());
        final long after = measure.applyAsLong(pair.fresh());
        log.trace("Never-worse measure {}->{} source={}", before, after, source);
        return after < Math.min(before, source);
    }

    // A source language with no negation list gives no bound, so the candidate is held to the current text alone.
    private static boolean losesNegation(final Pair pair) {
        final String sourceLanguage = pair.facts().sourceLanguage();
        final long source = sourceLanguage == null
                        || LanguageRules.bundled().negationWords(sourceLanguage).isEmpty()
                ? Long.MAX_VALUE
                : NegationCount.of(pair.source(), sourceLanguage);
        return fallsBelow(pair, text -> negations(text, pair.facts()), source);
    }

    private static long forbiddenLetters(final String text, final WindowFacts facts) {
        final String letters = LanguageRules.bundled().forbiddenLetters(facts.targetLanguage());
        return letters.isEmpty() ? 0L : TextGuards.count(text.toLowerCase(Locale.ROOT), letters);
    }

    private static long residue(final String text, final WindowFacts facts) {
        return LanguageIdentityCheck.residueCount(text, facts.sourceLanguage(), facts.targetLanguage());
    }

    private static long negations(final String text, final WindowFacts facts) {
        return NegationCount.of(text, facts.targetLanguage());
    }

    // A number the source holds that the candidate holds fewer times than both the current text and the source,
    // compared by its digits; a number only the current text holds is no loss.
    private static boolean losesNumber(final String old, final String fresh, final String source) {
        final Map<String, Long> before = numbers(old);
        final Map<String, Long> after = numbers(fresh);
        return numbers(source).entrySet().stream()
                .anyMatch(entry -> after.getOrDefault(entry.getKey(), 0L)
                        < Math.min(before.getOrDefault(entry.getKey(), 0L), entry.getValue()));
    }

    private static Map<String, Long> numbers(final String text) {
        return NUMBER.matcher(text)
                .results()
                .map(MatchResult::group)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
