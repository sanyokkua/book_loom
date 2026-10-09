package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.TermType;

/**
 * What a person who read the book says the model should conclude about it: the Book Brief's fields, who narrates and
 * each main character's type and gender. The owner writes one per book of the local gold set ({@code *.gold.json}
 * under {@code BOOKLOOM_CORPUS_DIR}, with {@code book} naming the file beside it); every committed judgement fixture
 * carries one for its invented book. The format is in {@code docs/DEVELOPMENT.md#judgement-evals}.
 *
 * @param book the book file, relative to the gold file's directory; null in a committed fixture, whose book is inline
 * @param sourceLanguage the book's language
 * @param targetLanguage the language it is translated into
 * @param brief the brief the person would choose
 * @param characters the main characters, each with the type and gender the review should end with
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record JudgementGold(
        @Nullable String book, String sourceLanguage, String targetLanguage, Brief brief, List<Person> characters) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Rejects a missing part and copies the characters. */
    JudgementGold {
        Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(brief, "brief");
        characters = characters == null ? List.of() : List.copyOf(characters);
    }

    /**
     * The person's brief. Free phrases ({@code genre}, {@code voiceEra}, {@code audience}) are what the person would
     * write; the model's genre is scored by {@code genreClass}, the words any one of which a right genre contains.
     *
     * @param genre the genre in the person's words, or null
     * @param genreClass lower-case words, any one of which a right genre contains ("cyberpunk", "science fiction");
     *     empty when the genre is not scored
     * @param register the register
     * @param voiceEra the voice and era phrase, or null
     * @param audience the readers, or null
     * @param names the name policy's constant name ({@code TRANSLATE}, {@code TRANSLITERATE}, {@code KEEP_ORIGINAL}),
     *     or null for the default
     * @param narrator who narrates; {@link NarratorPerson#UNSPECIFIED} for a book whose narrators alternate
     * @param narratorGender the first-person narrator's gender, {@link Gender#UNKNOWN} when the book never shows it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Brief(
            @Nullable String genre,
            List<String> genreClass,
            Register register,
            @Nullable String voiceEra,
            @Nullable String audience,
            @Nullable String names,
            NarratorPerson narrator,
            Gender narratorGender) {

        /** Rejects a missing choice and copies the genre words. */
        Brief {
            Objects.requireNonNull(register, "register");
            Objects.requireNonNull(narrator, "narrator");
            Objects.requireNonNull(narratorGender, "narratorGender");
            genreClass = genreClass == null ? List.of() : List.copyOf(genreClass);
        }

        /** The narrator the brief states. */
        Narrator narratorOf() {
            return new Narrator(narrator, narratorGender);
        }

        /** The brief's text settings in {@link EvalBrief}'s keys; a field the person left out is absent. */
        Map<String, String> settings() {
            final Map<String, String> settings = new LinkedHashMap<>();
            settings.put("register", register.name());
            putIfSet(settings, "genre", genre);
            putIfSet(settings, "voiceEra", voiceEra);
            putIfSet(settings, "audience", audience);
            putIfSet(settings, "names", names);
            return settings;
        }

        private static void putIfSet(final Map<String, String> settings, final String key, @Nullable final String v) {
            if (v != null && !v.isBlank()) {
                settings.put(key, v.strip());
            }
        }
    }

    /**
     * A main character as the review should leave it.
     *
     * @param term the name as the book writes it, which is the glossary term
     * @param type the type
     * @param gender the gender; {@link Gender#UNKNOWN} when the book never shows it
     */
    record Person(String term, TermType type, Gender gender) {

        /** Rejects a missing part. */
        Person {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(gender, "gender");
        }
    }

    /** Reads an owner's gold file. */
    static JudgementGold read(final Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), JudgementGold.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the gold file " + file, e);
        }
    }

    /**
     * Whether a genre the model suggested is of the gold genre's class.
     *
     * @param words the class's lower-case words; empty means every genre counts
     * @param genre the genre the model suggested, or null when it named none
     * @return {@code true} if the class is empty or the genre holds one of its words, {@code false} otherwise
     */
    static boolean isOfClass(final List<String> words, @Nullable final String genre) {
        if (words.isEmpty()) {
            return true;
        }
        final String suggested = genre == null ? "" : genre.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(word -> suggested.contains(word.toLowerCase(Locale.ROOT)));
    }
}
