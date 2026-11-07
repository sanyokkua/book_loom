package ua.bookloom.pipeline.proofread;

import static ua.bookloom.util.contract.ArgNames.TARGET;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.checks.CharacterName;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.glossary.NameForms;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The mark list's own detectors, which read a decided text against the book map as it is now: a map name the source
 * holds whose target forms the text lacks, a recurring term written otherwise than its established rendering, and a
 * woman's name followed by a man's word where the person or the book gave the gender. The audit already lists one
 * source given different targets and a name spelled two ways, so they are not repeated here.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkDetectors {

    /**
     * The doubts the detectors raise about one decided segment.
     *
     * @param map the non-null glossary and lexicon as they are now
     * @param source the non-null source display text
     * @param target the non-null decided target display text
     * @param targetLanguage the non-null target language tag
     * @return one reason per doubt, in detector order; never null, empty when nothing is doubtful
     */
    static List<String> find(final MapNow map, final String source, final String target, final String targetLanguage) {
        final List<String> reasons = new ArrayList<>();
        for (final GlossaryEntry entry : map.glossary()) {
            if (isBinding(entry) && TermMatch.occursIn(entry.names(), source)) {
                formMissing(entry, target, targetLanguage).ifPresent(reasons::add);
                genderEnding(entry, target, targetLanguage).ifPresent(reasons::add);
            }
        }
        for (final LexiconEntry term : map.lexicon()) {
            offRendering(term, source, target).ifPresent(reasons::add);
        }
        return reasons;
    }

    /**
     * The book map as the detectors read it.
     *
     * @param glossary the glossary entries
     * @param lexicon the recurring terms
     */
    record MapNow(List<GlossaryEntry> glossary, List<LexiconEntry> lexicon) {

        /** Copies the lists. */
        MapNow {
            glossary = List.copyOf(glossary);
            lexicon = List.copyOf(lexicon);
        }
    }

    // A suggested target was never binding, so the text may rightly read otherwise.
    private static boolean isBinding(final GlossaryEntry entry) {
        final String target = entry.target();
        return !entry.isSuggested() && target != null && !target.isBlank();
    }

    private static Optional<String> formMissing(
            final GlossaryEntry entry, final String target, final String targetLanguage) {
        final String rendering = Objects.requireNonNull(entry.target(), TARGET);
        final Set<String> forms =
                NameForms.forms(rendering, entry.gender(), targetLanguage).all();
        if (WholeWord.anyOf(forms).matcher(target).find()) {
            return Optional.empty();
        }
        log.debug("Mark detector: a map name's target forms are missing entry={} forms={}", entry.id(), forms.size());
        return Optional.of("name form missing: " + entry.term() + " → " + rendering);
    }

    private static Optional<String> genderEnding(
            final GlossaryEntry entry, final String target, final String targetLanguage) {
        if (!entry.hasConfirmedGender()) {
            return Optional.empty();
        }
        final String rendering = Objects.requireNonNull(entry.target(), TARGET);
        final boolean wrong = !GenderChecks.named(LanguageRules.bundled().genderCheck(targetLanguage))
                .findCharacters(target, List.of(new CharacterName(rendering, entry.gender())))
                .isEmpty();
        if (!wrong) {
            return Optional.empty();
        }
        log.debug("Mark detector: a word disagrees with an enforced gender entry={}", entry.id());
        return Optional.of(genderEndingReason(rendering, entry.gender()));
    }

    /**
     * The reason of a word against an enforced gender, which a deferral of the same name and gender already gives.
     *
     * @param name the name as the target writes it
     * @param gender the enforced gender
     * @return the reason; never null
     */
    static String genderEndingReason(final String name, final Gender gender) {
        return "gender ending against: " + name + " — " + gender.name().toLowerCase(Locale.ROOT);
    }

    private static Optional<String> offRendering(final LexiconEntry term, final String source, final String target) {
        final Optional<String> rendering = keptRendering(term);
        if (rendering.isEmpty() || !TermMatch.occursIn(namesOf(term), source)) {
            return Optional.empty();
        }
        if (TermMatch.isNamedIn(rendering.get(), target)) {
            return Optional.empty();
        }
        log.debug("Mark detector: a recurring term is written otherwise term={}", term.term());
        return Optional.of("recurring term off its rendering: " + term.term() + " → " + rendering.get());
    }

    // The person's choice, the learned rendering or the most used verified one; a model's mere suggestion binds
    // nothing.
    private static Optional<String> keptRendering(final LexiconEntry term) {
        final String chosen = term.chosen();
        if (chosen != null) {
            return Optional.of(chosen);
        }
        final LexiconEntry.Learned learned = term.learned();
        return learned != null ? Optional.of(learned.text()) : term.majority();
    }

    private static List<String> namesOf(final LexiconEntry term) {
        final List<String> names = new ArrayList<>(List.of(term.term()));
        names.addAll(term.forms());
        return names;
    }
}
