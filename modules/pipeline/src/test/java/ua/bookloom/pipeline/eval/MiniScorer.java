package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind.Level;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniExpectations.GlossaryExpectation;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;

/**
 * Scores a run of the mini book against its expectations with production checks only ({@code GenderChecks} for the
 * narrator) and plain patterns. A check that does not apply to the run's name policy is left out, not passed.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MiniScorer {

    /** What one check found: nothing to report when it holds, else why not. */
    record Verdict(boolean ok, String detail) {

        static Verdict pass(final String detail) {
            return new Verdict(true, detail);
        }

        static Verdict fail(final String detail) {
            return new Verdict(false, detail);
        }
    }

    private static final String NARRATOR_RULES = "uk";
    private static final char STRESS_MARK = '\u0301';
    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");
    private static final Pattern LATIN_WORD = Pattern.compile("\\p{IsLatin}{2,}");
    private static final Pattern LATIN_LETTER = Pattern.compile("\\p{IsLatin}");
    private static final Pattern CYRILLIC_LETTER = Pattern.compile("\\p{IsCyrillic}");
    private static final Pattern RUSSIAN_ONLY = Pattern.compile("[ыэъёЫЭЪЁ]");
    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s\\-–—]+");
    // The singular informal address and the capitalised formal one; a formal address written in lower case cannot be
    // told
    // from the plural, so the check reads only the capitalised forms.
    private static final Pattern INFORMAL = Pattern.compile(
            "(?<![\\p{L}])(?:[Тт]и|[Тт]ебе|[Тт]обі|[Тт]обою|[Тт]вій|[Тт]воя|[Тт]воє|[Тт]вої|[Тт]вого|[Тт]воєму)(?![\\p{L}])");
    private static final Pattern FORMAL = Pattern.compile("(?<![\\p{L}])(?:Ви|Вас|Вам|Вами|Ваш\\p{L}*)(?![\\p{L}])");

    /**
     * Scores every expectation that applies to the run.
     *
     * @param expectations the expectations to meet
     * @param run what the run decided
     * @return one result per applicable expectation, every-block checks first, then the segments, then the glossary
     */
    static List<MiniResult> score(final MiniExpectations expectations, final MiniRun run) {
        Objects.requireNonNull(expectations, "expectations");
        Objects.requireNonNull(run, "run");
        log.debug(
                "Mini score every={} segments={} glossary={}",
                expectations.every().size(),
                expectations.segments().size(),
                expectations.glossary().size());
        final List<MiniResult> results = new ArrayList<>();
        expectations.every().stream()
                .filter(e -> applies(e.policies(), run.policy()))
                .map(e -> everyBlock(e, run))
                .forEach(results::add);
        expectations.segments().stream()
                .filter(e -> applies(e.policies(), run.policy()))
                .map(e -> segmentResult(e, run))
                .forEach(results::add);
        expectations.glossary().stream()
                .filter(e -> applies(e.policies(), run.policy()))
                .map(e -> result(e.id(), e.defect(), Level.SOFT, glossary(e, run), List.of(), List.of()))
                .forEach(results::add);
        return List.copyOf(results);
    }

    private static boolean applies(final List<NamePolicy> policies, final NamePolicy policy) {
        return policies.isEmpty() || policies.contains(policy);
    }

    private static MiniResult result(
            final String id,
            final String defect,
            final Level level,
            final Verdict verdict,
            final List<String> blocks,
            final List<String> failing) {
        log.debug("Mini expectation {} passed={} {}", id, verdict.ok(), verdict.detail());
        return new MiniResult(id, defect, verdict.ok(), verdict.detail(), level, blocks, failing);
    }

    private static MiniResult segmentResult(final Expectation expectation, final MiniRun run) {
        final Verdict verdict = segment(expectation, run);
        final List<String> blocks = MiniGraders.isGlossaryLevel(expectation.check())
                ? List.of()
                : expectation.segment() == null ? expectation.group() : List.of(expectation.segment());
        return result(
                expectation.id(),
                expectation.defect(),
                expectation.check().level(),
                verdict,
                blocks,
                verdict.ok() ? List.of() : blocks);
    }

    private static MiniResult everyBlock(final Expectation expectation, final MiniRun run) {
        final List<String> failing = new ArrayList<>();
        for (final Outcome outcome : run.outcomes().values()) {
            if (!check(expectation, List.of(outcome)).ok()) {
                failing.add(outcome.id());
            }
        }
        final boolean covered = !run.outcomes().isEmpty();
        final String detail = failing.isEmpty()
                ? "all " + run.outcomes().size() + " segments"
                : "failing: " + String.join(", ", failing);
        return result(
                expectation.id(),
                expectation.defect(),
                expectation.check().level(),
                new Verdict(covered && failing.isEmpty(), detail),
                List.copyOf(run.outcomes().keySet()),
                failing);
    }

    private static Verdict segment(final Expectation expectation, final MiniRun run) {
        if (MiniGraders.isGlossaryLevel(expectation.check())) {
            return MiniGraders.glossary(expectation, run.glossary());
        }
        final List<String> ids = expectation.segment() == null ? expectation.group() : List.of(expectation.segment());
        final List<Outcome> outcomes =
                ids.stream().map(run.outcomes()::get).filter(Objects::nonNull).toList();
        return outcomes.size() == ids.size()
                ? check(expectation, outcomes)
                : Verdict.fail("no segment for " + String.join(", ", ids));
    }

    private static Verdict check(final Expectation expectation, final List<Outcome> outcomes) {
        final Outcome first = outcomes.get(0);
        if (expectation.check() == CheckKind.NOT_FLAGGED) {
            return notFlagged(first);
        }
        if (expectation.check() == CheckKind.NO_FINDING) {
            return noFinding(first, expectation.args());
        }
        final String joined =
                outcomes.stream().map(o -> o.target() == null ? "" : o.target()).collect(Collectors.joining("\n"));
        if (first.target() == null) {
            return Verdict.fail("no target stored");
        }
        return grade(expectation, outcomes, joined);
    }

    private static Verdict grade(final Expectation expectation, final List<Outcome> outcomes, final String joined) {
        final Outcome first = outcomes.get(0);
        return switch (expectation.check()) {
            case NO_LATIN_EXCEPT -> noLatin(joined, expectation.args());
            case NO_RUSSIAN_LETTERS -> noRussianLetters(joined);
            case MATCHES -> matches(joined, expectation.args(), true);
            case ABSENT -> matches(joined, expectation.args(), false);
            case NARRATOR_MALE -> narratorMale(joined);
            case MIN_RATIO ->
                minRatio(first, Double.parseDouble(expectation.args().get(0)));
            case ADDRESS_SAME -> addressSame(joined);
            case NO_STRESS_MARK -> noStressMark(joined);
            case HEADING_NUMBER -> headingNumber(joined, expectation.args().get(0));
            case NEGATION_KEPT -> negationKept(joined, expectation.args());
            case NUMBERS_KEPT -> numbersKept(joined, expectation.args());
            case PLACEHOLDERS -> placeholders(joined, expectation.args());
            case TARGET_ALPHABET -> MiniGraders.targetAlphabet(joined, expectation.args());
            case FOREIGN_SCRIPT -> MiniGraders.foreignScript(joined);
            case NO_SOURCE_LINE -> MiniGraders.noSourceLine(first.source(), Objects.requireNonNull(first.target()));
            case SOURCE_ONLY_WORDS -> MiniGraders.sourceOnlyWords(joined, expectation.args());
            case DIALOGUE_DASH -> MiniGraders.dialogueDash(first.source(), Objects.requireNonNull(first.target()));
            case VOCATIVE -> MiniGraders.vocative(joined, expectation.args());
            case NARRATOR_GENDER -> MiniGraders.narratorGender(joined, expectation.args());
            case ONE_SPELLING_PER_LEMMA -> MiniGraders.oneSpelling(joined, expectation.args());
            case GLOSSARY_ONE_ENTRY, GLOSSARY_GENDER -> throw new IllegalStateException("glossary level");
            case NOT_FLAGGED, NO_FINDING -> throw new IllegalStateException("handled above");
        };
    }

    private static Verdict notFlagged(final Outcome outcome) {
        final boolean usable = outcome.target() != null
                && outcome.status() != SegmentStatus.FLAGGED
                && outcome.status() != SegmentStatus.PENDING;
        return usable ? Verdict.pass(outcome.status().name()) : Verdict.fail("status " + outcome.status());
    }

    private static Verdict noFinding(final Outcome outcome, final List<String> raisedBy) {
        final List<String> found = outcome.findings().stream()
                .map(QaFinding::raisedBy)
                .filter(name -> raisedBy.stream().anyMatch(name::contains))
                .toList();
        return found.isEmpty() ? Verdict.pass("none of " + raisedBy) : Verdict.fail("finding " + found);
    }

    private static Verdict noLatin(final String target, final List<String> allowed) {
        String rest = target;
        for (final String word : allowed) {
            rest = rest.replace(word, " ");
        }
        final List<String> left =
                LATIN_WORD.matcher(rest).results().map(m -> m.group()).toList();
        return left.isEmpty() ? Verdict.pass("no Latin word") : Verdict.fail("Latin left: " + left);
    }

    private static Verdict noRussianLetters(final String target) {
        final List<String> found =
                RUSSIAN_ONLY.matcher(target).results().map(m -> m.group()).toList();
        return found.isEmpty() ? Verdict.pass("none") : Verdict.fail("Russian letters " + found);
    }

    private static Verdict matches(final String target, final List<String> patterns, final boolean wanted) {
        for (final String pattern : patterns) {
            final boolean found = Pattern.compile(pattern).matcher(target).find();
            if (found != wanted) {
                return Verdict.fail((wanted ? "no match for " : "unwanted match for ") + pattern);
            }
        }
        return Verdict.pass(wanted ? "matched" : "absent");
    }

    private static Verdict narratorMale(final String target) {
        final int slips =
                GenderChecks.named(NARRATOR_RULES).find(target, Gender.MALE).size();
        return slips == 0
                ? Verdict.pass("no feminine form after я")
                : Verdict.fail(slips + " feminine form(s) after я");
    }

    private static Verdict minRatio(final Outcome outcome, final double ratio) {
        final String target = Objects.requireNonNull(outcome.target());
        final double actual =
                (double) target.length() / Math.max(1, outcome.source().length());
        return actual >= ratio
                ? Verdict.pass("%.2f of the source".formatted(actual))
                : Verdict.fail("%.2f of the source, wanted %.2f".formatted(actual, ratio));
    }

    private static Verdict noStressMark(final String target) {
        return target.indexOf(STRESS_MARK) < 0 ? Verdict.pass("none") : Verdict.fail("stress mark U+0301 in target");
    }

    private static Verdict headingNumber(final String target, final String number) {
        return target.strip().startsWith(number)
                ? Verdict.pass("starts with " + number)
                : Verdict.fail("does not start with " + number);
    }

    private static Verdict negationKept(final String target, final List<String> args) {
        final int wanted = Integer.parseInt(args.get(1));
        final Pattern word = Pattern.compile(
                "(?<![\\p{L}])" + Pattern.quote(args.get(0)) + "(?![\\p{L}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        final long found = word.matcher(target).results().count();
        return found >= wanted
                ? Verdict.pass(found + " x " + args.get(0))
                : Verdict.fail(found + " x " + args.get(0) + ", wanted " + wanted);
    }

    private static Verdict numbersKept(final String target, final List<String> figures) {
        final List<String> missing =
                figures.stream().filter(f -> !target.contains(f)).toList();
        return missing.isEmpty() ? Verdict.pass("all figures kept") : Verdict.fail("figures lost: " + missing);
    }

    private static Verdict placeholders(final String target, final List<String> tokens) {
        final List<String> found =
                TOKEN.matcher(target).results().map(m -> m.group()).sorted().toList();
        final List<String> wanted = tokens.stream().sorted().toList();
        return found.equals(wanted)
                ? Verdict.pass("tokens " + wanted)
                : Verdict.fail("tokens " + found + ", wanted " + wanted);
    }

    private static Verdict addressSame(final String targets) {
        final boolean informal = INFORMAL.matcher(targets).find();
        final boolean formal = FORMAL.matcher(targets).find();
        return informal && formal
                ? Verdict.fail("ти-forms and Ви-forms together")
                : Verdict.pass(informal ? "ти" : formal ? "Ви" : "neither");
    }

    private static Verdict glossary(final GlossaryExpectation expectation, final MiniRun run) {
        final Optional<GlossaryEntry> entry = find(expectation, run.glossary());
        if (expectation.absent()) {
            return entry.isEmpty()
                    ? Verdict.pass("not in the glossary")
                    : Verdict.fail("kept: " + entry.get().term());
        }
        if (entry.isEmpty()) {
            return expectation.optional()
                    ? Verdict.pass("not in the glossary (optional)")
                    : Verdict.fail("missing: " + expectation.terms());
        }
        return entryChecks(expectation, entry.get());
    }

    private static Optional<GlossaryEntry> find(
            final GlossaryExpectation expectation, final List<GlossaryEntry> glossary) {
        return expectation.terms().stream()
                .flatMap(term -> glossary.stream()
                        .filter(e -> e.term().equals(term) || e.forms().contains(term)))
                .findFirst();
    }

    private static Verdict entryChecks(final GlossaryExpectation expectation, final GlossaryEntry entry) {
        final String target = entry.target() == null ? "" : entry.target();
        final List<String> misses = new ArrayList<>();
        miss(misses, expectation.type() != null && entry.type() != expectation.type(), "type " + entry.type());
        miss(misses, expectation.typeNot() != null && entry.type() == expectation.typeNot(), "type " + entry.type());
        identityMisses(misses, expectation, entry);
        targetMisses(misses, expectation, entry, target);
        return misses.isEmpty()
                ? Verdict.pass(entry.term() + " -> '" + target + "'")
                : Verdict.fail(entry.term() + ": " + String.join("; ", misses));
    }

    private static void identityMisses(
            final List<String> misses, final GlossaryExpectation expectation, final GlossaryEntry entry) {
        miss(
                misses,
                expectation.gender() != null && entry.gender() != expectation.gender(),
                "gender " + entry.gender());
        miss(
                misses,
                expectation.verified() != null && isSettled(entry) != expectation.verified(),
                "gender " + entry.gender() + " from " + entry.genderOrigin());
        miss(
                misses,
                expectation.locked() != null && entry.locked() != expectation.locked(),
                "locked=" + entry.locked());
    }

    private static void targetMisses(
            final List<String> misses,
            final GlossaryExpectation expectation,
            final GlossaryEntry entry,
            final String target) {
        miss(misses, expectation.targetCyrillic() && !isCyrillic(target), "target '" + target + "'");
        miss(misses, expectation.targetKept() && !target.equals(entry.term()), "target '" + target + "'");
        miss(
                misses,
                expectation.targetMatches() != null
                        && !Pattern.compile(expectation.targetMatches())
                                .matcher(target)
                                .find(),
                "target '" + target + "'");
        miss(
                misses,
                words(target) < expectation.minWords(),
                "target '" + target + "' has " + words(target) + " word(s)");
    }

    private static void miss(final List<String> misses, final boolean missed, final String what) {
        if (missed) {
            misses.add(what);
        }
    }

    private static boolean isSettled(final GlossaryEntry entry) {
        return entry.gender() != Gender.UNKNOWN && entry.genderOrigin() != Origin.LIKELY;
    }

    private static boolean isCyrillic(final String target) {
        return CYRILLIC_LETTER.matcher(target).find()
                && !LATIN_LETTER.matcher(target).find();
    }

    private static int words(final String target) {
        return target.isBlank() ? 0 : WORD_SPLIT.split(target.strip()).length;
    }
}
