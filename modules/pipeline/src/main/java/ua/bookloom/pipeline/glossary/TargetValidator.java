package ua.bookloom.pipeline.glossary;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.pipeline.glossary.IdentityRule.Kept;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Standing;
import ua.bookloom.pipeline.glossary.TransliterationCheck.Problem;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;
import ua.bookloom.util.text.Edges;
import ua.bookloom.util.text.StressMarks;

/**
 * The rules a target must meet in the target language (15i.N6, 15j.J1, 15j.J4, 15j.J10, design D4). {@link #standing}
 * judges a suggestion as it arrives (identity, alphabet, and for a name that is not a speaking name every capitalised
 * word kept, the letter table under Transliterate and the dictionary form), and then {@link #suggestionRefusal}; a
 * refusal of either is asked once more. {@link #validate} judges a target before anything accepts it by the language
 * file's keys (letters, Latin, stress marks, capitals, word count, the Brief's conventions, an accepted name's forms) and
 * raises the review flags, computed each time and never stored. Speaking names and brands come from the readings.
 */
@Slf4j
final class TargetValidator implements SuggestionReplies.Judge {

    private static final Pattern WORD_PARTS = Pattern.compile("[\\s\\-\u2010\u2011\u2013]+");

    /** Why a target is refused. */
    enum Reason {
        /** A letter the target language's file forbids. */
        FORBIDDEN_LETTER("letter not in the target alphabet"),
        /** A Latin letter in a target of another script, the term no identifier and not kept as written. */
        LATIN("Latin letter in the target"),
        /** A stress mark the language asks to remove. */
        STRESS_MARK("stress mark in the target"),
        /** No capital at all for a capitalised name. */
        CAPITALISATION("capitalisation differs"),
        /** A name's words counted by their hyphenated parts differ in number. */
        WORD_COUNT("different number of words"),
        /** The other alternative of a convention the Brief stores; the text names both. */
        CONVENTION("spelled against the book's convention"),
        /** Not a form the accepted target's table generates. */
        RESPELLING("a different spelling of the accepted name");

        private final String label;

        Reason(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** What a row is flagged for on review; a flagged row is kept out of Accept all, but nothing is refused. */
    enum Flag {
        /** A name's target ends like an oblique or a vocative form. */
        OBLIQUE_ENDING("not the dictionary form"),
        /** A name's target counts another number of words than its term. */
        WORD_COUNT_DIFFERS("different number of words");

        private final String label;

        Flag(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * One refusal.
     *
     * @param reason the rule that refused
     * @param text the reason in the person's words
     */
    record Refusal(Reason reason, String text) {}

    /**
     * What the rules say of one target.
     *
     * @param refusals the refusals in rule order; empty when every rule passes
     * @param flags the review flags; empty when none applies
     */
    record Verdict(List<Refusal> refusals, List<Flag> flags) {

        Verdict {
            refusals = List.copyOf(refusals);
            flags = List.copyOf(flags);
        }

        List<Reason> rejections() {
            return refusals.stream().map(Refusal::reason).toList();
        }

        /** The refusals' and the flags' words, each once. */
        List<String> reasons() {
            return Stream.concat(
                            refusals.stream().map(Refusal::text), flags.stream().map(Flag::label))
                    .distinct()
                    .toList();
        }

        /** Whether Accept all may take the target: no refusal and no flag. */
        boolean passes() {
            return refusals.isEmpty() && flags.isEmpty();
        }
    }

    private final String targetLanguage;
    private final @Nullable String analysisLanguage;
    private final NamePolicy policy;
    private final TargetContext context;
    private final Map<String, List<String>> table;
    private final String forbiddenLetters;
    private final List<String> caseEndings;
    private final String openFinals;
    private final Set<String> articles;
    private final TargetConventions conventions;

    TargetValidator(final CallFrame frame, final NamePolicy policy, final TargetContext context) {
        this(frame.targetLanguage(), frame.analysisLanguage(), policy, context, Map.of());
    }

    private TargetValidator(
            final String targetLanguage,
            @Nullable final String analysisLanguage,
            final NamePolicy policy,
            final TargetContext context,
            final Map<String, String> chosen) {
        this.targetLanguage = targetLanguage;
        this.analysisLanguage = analysisLanguage;
        this.policy = policy;
        this.context = context;
        final LanguageRules rules = LanguageRules.bundled();
        this.table = rules.translit(targetLanguage);
        this.forbiddenLetters = rules.forbiddenLetters(targetLanguage);
        this.caseEndings = rules.wordsOf(targetLanguage, "nonNominativeEndings");
        this.openFinals =
                analysisLanguage == null ? "" : String.join("", rules.wordsOf(analysisLanguage, "openNameFinals"));
        this.articles = analysisLanguage == null ? Set.of() : Set.copyOf(rules.wordsOf(analysisLanguage, "articles"));
        this.conventions = TargetConventions.of(targetLanguage, chosen);
    }

    /**
     * A validator for targets outside a suggestion run.
     *
     * @param targetLanguage the non-null target language tag
     * @param analysisLanguage the language the code reads the book in, or null for the generic rules
     * @param policy the Book Brief's name policy; never null
     * @param context the held entries and what the readings said of them; never null
     * @param chosen the Brief's declared conventions, by name; never null
     * @return the validator
     */
    static TargetValidator of(
            final String targetLanguage,
            @Nullable final String analysisLanguage,
            final NamePolicy policy,
            final TargetContext context,
            final Map<String, String> chosen) {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(chosen, "chosen");
        log.debug(
                "Target validator target={} analysis={} policy={} conventions={}",
                targetLanguage,
                analysisLanguage,
                policy,
                chosen.keySet());
        return new TargetValidator(targetLanguage, analysisLanguage, policy, context, chosen);
    }

    /**
     * Where a rendering stands against the rules.
     *
     * @param entry the entry the rendering is for
     * @param rendering the stripped, one-line rendering
     * @return the refusing {@link Standing} for a refused one, {@link Standing#VERBATIM} for an identifier kept as
     *     written, {@link Standing#OPEN} otherwise
     */
    @Override
    public Standing standing(final GlossaryEntry entry, final String rendering) {
        final String term = entry.term();
        if (term.strip().equalsIgnoreCase(rendering.strip())) {
            return copyStanding(entry, rendering);
        }
        if (IdentityRule.isIdentifier(term)) {
            log.debug("Rendering of entry {} changes an identifier", entry.id());
            return Standing.IDENTIFIER;
        }
        if (hasForbiddenLetter(rendering)) {
            log.debug("Rendering of entry {} holds a letter the target language does not use", entry.id());
            log.trace("Rendering '{}' of '{}' holds a letter outside the alphabet", rendering, term);
            return Standing.ALPHABET;
        }
        final boolean isName = SuggestionLocks.isNameLike(entry, context);
        if (policy == NamePolicy.KEEP_ORIGINAL || !isName || SuggestionLocks.isTranslatable(entry, context)) {
            return Standing.OPEN;
        }
        return nameStanding(entry, rendering);
    }

    /**
     * What the target language's rules say of a target for an entry: the refusals, and the review flags. With an
     * accepted (pinned or locked) target on the entry, the candidate must be one of its forms.
     *
     * @param entry the entry the target is for; never null
     * @param candidate the target to judge; never null
     * @return the verdict; never null
     */
    Verdict validate(final GlossaryEntry entry, final String candidate) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(candidate, "candidate");
        final boolean isName = SuggestionLocks.isNameLike(entry, context);
        final boolean isWordCountOff = isName && parts(entry.term()) != parts(candidate);
        final List<Refusal> refusals = Stream.of(
                        forbidden(candidate),
                        latin(entry, candidate),
                        stressMark(candidate),
                        capitals(entry, candidate, isName),
                        isWordCountOff ? Optional.of(refusal(Reason.WORD_COUNT)) : Optional.<Refusal>empty(),
                        convention(candidate),
                        respelling(entry, candidate))
                .flatMap(Optional::stream)
                .toList();
        final List<Flag> flags = new ArrayList<>();
        if (isName && isObliqueLooking(entry.term(), candidate)) {
            flags.add(Flag.OBLIQUE_ENDING);
        }
        if (isWordCountOff) {
            flags.add(Flag.WORD_COUNT_DIFFERS);
        }
        final Verdict verdict = new Verdict(refusals, flags);
        log.debug("Target of entry {} judged: refusals={} flags={}", entry.id(), verdict.rejections(), flags);
        log.trace("Target '{}' of '{}': {}", candidate, entry.term(), verdict.reasons());
        return verdict;
    }

    // The first refusal of validate, as a suggestion arrives: a different word count is no refusal where the words may
    // rightly differ (a speaking name, or a name under a policy that translates: Silverbrook, Срібний Струмок).
    @Override
    public @Nullable String suggestionRefusal(final GlossaryEntry entry, final String rendering) {
        final boolean isCountFree =
                policy != NamePolicy.TRANSLITERATE || SuggestionLocks.isTranslatable(entry, context);
        final Optional<Refusal> first = validate(entry, rendering).refusals().stream()
                .filter(refusal -> !(isCountFree && refusal.reason() == Reason.WORD_COUNT))
                .findFirst();
        first.ifPresent(refusal -> log.debug("Suggestion for entry {} refused: {}", entry.id(), refusal.reason()));
        return first.map(Refusal::text).orElse(null);
    }

    private Optional<Refusal> forbidden(final String candidate) {
        return hasForbiddenLetter(candidate) ? Optional.of(refusal(Reason.FORBIDDEN_LETTER)) : Optional.empty();
    }

    private Optional<Refusal> latin(final GlossaryEntry entry, final String candidate) {
        final Optional<Script> script = Languages.scriptOf(targetLanguage);
        final boolean isOtherScript =
                script.isPresent() && !script.get().letterScripts().contains(UnicodeScript.LATIN);
        final boolean isKept =
                IdentityRule.isIdentifier(entry.term()) || SuggestionLocks.isKeptAsWritten(entry, policy, context);
        final boolean hasLatin = candidate
                .codePoints()
                .filter(Character::isLetter)
                .anyMatch(point -> UnicodeScript.of(point) == UnicodeScript.LATIN);
        return isOtherScript && !isKept && hasLatin ? Optional.of(refusal(Reason.LATIN)) : Optional.empty();
    }

    private Optional<Refusal> stressMark(final String candidate) {
        final boolean isMarked = TargetText.stripsStress(targetLanguage) && StressMarks.count(candidate) > 0;
        return isMarked ? Optional.of(refusal(Reason.STRESS_MARK)) : Optional.empty();
    }

    // A translated common word may stand in lower case (місто for City), and so may the later part of a hyphenated
    // name (Бейкер-стріт): only a target with no capital at all for a capitalised name is refused.
    private static Optional<Refusal> capitals(final GlossaryEntry entry, final String candidate, final boolean isName) {
        final boolean isLost = isName
                && entry.term().codePoints().anyMatch(Character::isUpperCase)
                && candidate.codePoints().noneMatch(Character::isUpperCase);
        return isLost ? Optional.of(refusal(Reason.CAPITALISATION)) : Optional.empty();
    }

    private Optional<Refusal> convention(final String candidate) {
        return conventions.refusal(candidate).map(text -> new Refusal(Reason.CONVENTION, text));
    }

    // An accepted target fixes the spelling: a pinned one may take any form its table generates, a locked one none.
    private Optional<Refusal> respelling(final GlossaryEntry entry, final String candidate) {
        final String accepted = entry.target();
        if (entry.targetState() == TargetState.SUGGESTED
                || accepted == null
                || accepted.isBlank()
                || conventions.fold(accepted).equals(conventions.fold(candidate))) {
            return Optional.empty();
        }
        final Set<String> forms = entry.locked()
                ? Set.of(accepted)
                : NameForms.forms(accepted, entry.gender(), targetLanguage).all();
        final String folded = conventions.fold(candidate);
        final boolean isForm = forms.stream().map(conventions::fold).anyMatch(folded::equals);
        log.debug("Candidate for entry {} is {} of its accepted target", entry.id(), isForm ? "a form" : "no form");
        return isForm ? Optional.empty() : Optional.of(refusal(Reason.RESPELLING));
    }

    private boolean isObliqueLooking(final String term, final String candidate) {
        return isCaseForm(term, candidate) || NameForms.looksVocative(lastWord(candidate), targetLanguage);
    }

    private static int parts(final String text) {
        return (int) WORD_PARTS
                .splitAsStream(text.strip())
                .filter(part -> !part.isEmpty())
                .count();
    }

    private static Refusal refusal(final Reason reason) {
        return new Refusal(reason, reason.label());
    }

    private Standing nameStanding(final GlossaryEntry entry, final String rendering) {
        final String term = entry.term();
        final Optional<Problem> problem =
                TransliterationCheck.problem(term, rendering, table, policy == NamePolicy.TRANSLITERATE, articles);
        if (problem.isPresent()) {
            log.debug("Rendering of entry {}: {}", entry.id(), problem.get());
            log.trace("Rendering '{}' of '{}': {}", rendering, term, problem.get());
            return problem.get() == Problem.LOST_WORD ? Standing.LOST_WORD : Standing.LETTERS;
        }
        if (policy == NamePolicy.TRANSLITERATE && isCaseForm(term, rendering)) {
            log.debug("Rendering of entry {} is a case form of a name that ends in a consonant", entry.id());
            log.trace("Rendering '{}' of '{}' ends in a case ending", rendering, term);
            return Standing.OBLIQUE;
        }
        return Standing.OPEN;
    }

    private boolean hasForbiddenLetter(final String rendering) {
        return rendering.toLowerCase(Locale.ROOT).codePoints().anyMatch(point -> forbiddenLetters.indexOf(point) >= 0);
    }

    // A name that ends in a consonant sound stays so in the dictionary form: the rendering is a case form only when it
    // is the source's own transliteration plus an oblique ending (Korvel, not Корвелом) - the letter before the ending
    // stands for the source's last letter (language file translit) and the stem is no shorter than the source. A name
    // that merely ends like one (Том, Львів) or is no transliteration of the source is kept; a lower-case last word is
    // a translated common word (річка); a language that lists no open final letters judges nothing.
    private boolean isCaseForm(final String term, final String rendering) {
        final String name = lastWord(term).toLowerCase(Locale.ROOT);
        final String word = lastWord(rendering);
        if (openFinals.isEmpty() || name.isEmpty() || word.isEmpty() || !Character.isUpperCase(word.codePointAt(0))) {
            return false;
        }
        final String finalLetter = name.substring(name.length() - 1);
        final List<String> letters = table.getOrDefault(finalLetter, List.of());
        final String lower = word.toLowerCase(Locale.ROOT);
        return Character.isLetter(finalLetter.charAt(0))
                && !openFinals.contains(finalLetter)
                && caseEndings.stream().anyMatch(ending -> isStemPlus(lower, ending, name.length(), letters));
    }

    private static boolean isStemPlus(
            final String word, final String ending, final int sourceLength, final List<String> letters) {
        final int stemLength = word.length() - ending.length();
        return word.endsWith(ending)
                && stemLength >= sourceLength
                && letters.contains(word.substring(stemLength - 1, stemLength));
    }

    private static String lastWord(final String name) {
        return Edges.trailingRun(name.strip(), c -> !Character.isWhitespace(c) && c != '-');
    }

    private Standing copyStanding(final GlossaryEntry entry, final String rendering) {
        final String term = entry.term();
        if (IdentityRule.refuses(term, rendering, analysisLanguage, targetLanguage, Kept.NOT_KEPT)) {
            return SuggestionLocks.isTechnicalTerm(entry, context) ? Standing.VERBATIM : Standing.COPY;
        }
        return IdentityRule.isIdentifier(term) ? Standing.VERBATIM : Standing.OPEN;
    }
}
