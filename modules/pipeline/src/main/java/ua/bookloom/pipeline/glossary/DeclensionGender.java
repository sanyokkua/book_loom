package ua.bookloom.pipeline.glossary;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * A name's gender read from how the book declines it, by rule: when at least one attested form of the entry reads as
 * a non-nominative case of the entry's own term ({@link NameForms#declensions}), every such reading and every
 * nominative reading of the term have one gender, every row they come from is listed in the source file's
 * {@code paradigmGender}, and no pronoun or past-tense verb after the name says the other gender, the entry gets that
 * gender with the origin rule — shown, never enforced. Only a person-like entry (character, term or other) whose gender
 * is still unknown and was never tried is read; its type is never changed. A row a name of either gender declines by
 * (the Russian {@code -а}: {@code Никита}) is left out of {@code paradigmGender}, so it decides nothing.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeclensionGender {

    private static final Set<TermType> PERSON_LIKE = EnumSet.of(TermType.CHARACTER, TermType.TERM, TermType.OTHER);

    /**
     * The entry with the gender its declension shows.
     *
     * @param entry the non-null entry
     * @param texts the non-null visible texts of the book, where a contrary pronoun or verb is looked for
     * @param language the source language tag, or null when it is not known (nothing is set)
     * @return the entry with a gender by rule, or {@code entry} itself when nothing is to be set
     */
    public static GlossaryEntry seeded(
            final GlossaryEntry entry, final List<String> texts, @Nullable final String language) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(texts, "texts");
        final boolean eligible = entry.gender() == Gender.UNKNOWN
                && !entry.genderSeedTried()
                && !entry.locked()
                && PERSON_LIKE.contains(entry.type())
                && !entry.forms().isEmpty()
                && NameForms.hasParadigm(language);
        if (!eligible) {
            return entry;
        }
        final Gender declined = genderOf(entry, Objects.requireNonNull(language, "language"));
        if (declined == Gender.UNKNOWN) {
            return entry;
        }
        final PronounGender.Evidence evidence = PronounGender.of(entry, texts, language);
        final int contrary = declined == Gender.MALE ? evidence.female() : evidence.male();
        if (contrary > 0) {
            log.debug("Declension gender {} contradicted by {} votes: nothing set", declined, contrary);
            log.trace("Declension gender of entry {} contradicted", entry.id());
            return entry;
        }
        log.debug("Declension gender {} set by rule", declined);
        log.trace("Glossary entry {} gets the gender {} by rule from its declension", entry.id(), declined);
        return entry.withGenderFrom(declined, Origin.RULE);
    }

    /**
     * The entries as {@link #seeded(GlossaryEntry, List, String)} would leave them, in order.
     *
     * @param entries the non-null entries
     * @param texts the non-null visible texts of the book
     * @param language the source language tag, or null when it is not known
     * @return a list of the same size; never null
     */
    public static List<GlossaryEntry> seeded(
            final List<GlossaryEntry> entries, final List<String> texts, @Nullable final String language) {
        Objects.requireNonNull(entries, "entries");
        final List<GlossaryEntry> seeded =
                entries.stream().map(entry -> seeded(entry, texts, language)).toList();
        log.debug(
                "Declension gender language={} entries={} set={}",
                language,
                entries.size(),
                countChanged(entries, seeded));
        return seeded;
    }

    // The one gender the term's nominative rows and its forms' oblique rows share, all of them listed; else unknown.
    private static Gender genderOf(final GlossaryEntry entry, final String language) {
        final Set<String> listed = Set.copyOf(LanguageRules.bundled().wordsOf(language, "paradigmGender"));
        final String key = Occurrences.keyOf(entry.term());
        final List<NameForms.Declension> obliques = entry.forms().stream()
                .flatMap(form -> NameForms.declensions(form, language).stream())
                .filter(declension -> !declension.nominative())
                .filter(declension -> Occurrences.keyOf(declension.lemma()).equals(key))
                .toList();
        final List<NameForms.Declension> nominatives = NameForms.declensions(entry.term(), language).stream()
                .filter(NameForms.Declension::nominative)
                .filter(declension -> declension.gender() != Gender.UNKNOWN)
                .toList();
        if (obliques.isEmpty()) {
            return Gender.UNKNOWN;
        }
        final Set<Gender> genders = EnumSet.noneOf(Gender.class);
        obliques.forEach(declension -> genders.add(declension.gender()));
        nominatives.forEach(declension -> genders.add(declension.gender()));
        final boolean allListed = obliques.stream().allMatch(declension -> listed.contains(rowEnding(declension)));
        log.trace("Declension of {}: rows {}, genders {}, all listed {}", entry.term(), obliques, genders, allListed);
        if (!allListed || genders.size() != 1 || genders.contains(Gender.UNKNOWN)) {
            return Gender.UNKNOWN;
        }
        return genders.iterator().next();
    }

    private static String rowEnding(final NameForms.Declension declension) {
        final int bar = declension.row().indexOf('|');
        return bar < 0 ? declension.row() : declension.row().substring(0, bar);
    }

    private static long countChanged(final List<GlossaryEntry> before, final List<GlossaryEntry> after) {
        long changed = 0;
        for (int index = 0; index < before.size(); index++) {
            changed += before.get(index).equals(after.get(index)) ? 0 : 1;
        }
        return changed;
    }
}
