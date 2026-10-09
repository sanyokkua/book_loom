package ua.bookloom.pipeline.setup;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.GivenNames;
import ua.bookloom.pipeline.prompt.AddressTerms;

/**
 * Decides whether a quote the model gave for the narrator's gender shows that gender in words the code can check. Two
 * kinds of quote do: one that calls the narrator by a name, or in which the narrator gives it ("Hang on to your ass,
 * Jack"), when that name stands in the quote and is a glossary person of known gender or a listed first name, whose
 * gender it then is; and one that addresses the narrator with a word of the source language's {@code addressTerms}
 * ({@code ma'am}, {@code пане}), when the quote holds words of one gender only. The model's own word for the gender never
 * decides it. That the quote exists in the sample is checked by the caller.
 */
@Slf4j
final class NarratorGenderEvidence {

    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{M}']+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final List<GlossaryEntry> people;
    private final String languageTag;
    private final Map<String, Gender> addressTerms;

    /**
     * Prepares the check for one book.
     *
     * @param glossary the book's glossary entries; only characters of a known gender are used
     * @param languageTag the source language's tag, which chooses the first-name list and the address terms
     */
    NarratorGenderEvidence(final List<GlossaryEntry> glossary, final String languageTag) {
        Objects.requireNonNull(glossary, "glossary");
        this.languageTag = Objects.requireNonNull(languageTag, "languageTag");
        this.people = glossary.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER)
                .filter(entry -> isKnown(entry.gender()))
                .toList();
        this.addressTerms = AddressTerms.of(languageTag);
        log.debug(
                "Narrator gender evidence language={} glossaryPeople={} addressTerms={}",
                languageTag,
                people.size(),
                addressTerms.size());
    }

    /**
     * The gender a quote shows, by the name the model read from it or else by its words of address.
     *
     * @param quote the quote, already found in the sample
     * @param name the narrator's name the model read from the quote; blank when it named none
     * @return the gender the name or the address terms prove, or empty if the quote proves none
     */
    Optional<Gender> genderOf(final String quote, final String name) {
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(name, "name");
        final List<String> words = words(quote);
        if (!name.isBlank()) {
            final Optional<Gender> named = byName(words, name.strip());
            log.debug("Narrator name in the quote proves gender {}", named.orElse(Gender.UNKNOWN));
            return named;
        }
        final Set<Gender> addressed =
                words.stream().map(addressTerms::get).filter(Objects::nonNull).collect(Collectors.toSet());
        log.debug("Narrator address terms in the quote show genders {}", addressed);
        return addressed.size() == 1 ? Optional.of(addressed.iterator().next()) : Optional.empty();
    }

    private Optional<Gender> byName(final List<String> quoteWords, final String name) {
        final List<String> nameWords = words(name);
        if (nameWords.isEmpty() || !contains(quoteWords, nameWords)) {
            log.debug("The narrator's name is not in its quote");
            return Optional.empty();
        }
        final Set<Gender> glossary = people.stream()
                .filter(entry -> namedBy(entry, name, nameWords))
                .map(GlossaryEntry::gender)
                .collect(Collectors.toSet());
        if (!glossary.isEmpty()) {
            log.debug("The narrator's name matches glossary people of genders {}", glossary);
            return glossary.size() == 1 ? Optional.of(glossary.iterator().next()) : Optional.empty();
        }
        return GivenNames.genderOf(name, languageTag);
    }

    // A person is named by the whole term ("Rikki Wildside") or by one word of it ("Rikki").
    private static boolean namedBy(final GlossaryEntry entry, final String name, final List<String> nameWords) {
        if (entry.term().strip().equalsIgnoreCase(name)) {
            return true;
        }
        return nameWords.size() == 1 && words(entry.term()).contains(nameWords.getFirst());
    }

    private static boolean contains(final List<String> words, final List<String> run) {
        for (int at = 0; at + run.size() <= words.size(); at++) {
            if (words.subList(at, at + run.size()).equals(run)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> words(final String text) {
        final String marks = WHITESPACE.matcher(text.replace('’', '\'')).replaceAll(" ");
        return Arrays.stream(NOT_A_WORD.split(marks.toLowerCase(Locale.ROOT)))
                .map(word -> word.replaceAll("^'+|'+$", ""))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static boolean isKnown(final Gender gender) {
        return gender == Gender.MALE || gender == Gender.FEMALE;
    }
}
