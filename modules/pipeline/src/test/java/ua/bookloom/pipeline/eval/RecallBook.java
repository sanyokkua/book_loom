package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;

/**
 * One book of the detector-recall suite, as its {@code book.json} names it: the source, the exported book beside it,
 * the export's glossary side file and the languages and narrator the run held the text to. Nothing here holds book
 * text; the files it names live outside the repository.
 *
 * @param name the book directory's name, the report's book label
 * @param dir the book directory, where {@code gold.jsonl} is read and {@code segments.jsonl} written
 * @param source the source book file
 * @param exported the exported book file
 * @param glossary the export's glossary CSV, or {@code null} when the book names none
 * @param sourceLanguage the source language tag, {@code en} when unstated
 * @param targetLanguage the target language tag, {@code uk} when unstated
 * @param narrator the narrator the gender checks hold the text to; unspecified when unstated
 */
record RecallBook(
        String name,
        Path dir,
        Path source,
        Path exported,
        @Nullable Path glossary,
        String sourceLanguage,
        String targetLanguage,
        Narrator narrator) {

    /** The file that makes a directory a book of the suite. */
    static final String FILE = "book.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Rejects missing parts. */
    RecallBook {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(exported, "exported");
        Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(narrator, "narrator");
    }

    /** Reads a book directory's {@code book.json}; {@code source} and {@code exported} are required. */
    static RecallBook load(final Path dir) {
        final JsonNode json;
        try {
            json = MAPPER.readTree(Files.readString(dir.resolve(FILE), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final String glossary = json.path("glossary").asText("");
        return new RecallBook(
                String.valueOf(dir.getFileName()),
                dir,
                dir.resolve(required(json, "source")),
                dir.resolve(required(json, "exported")),
                glossary.isEmpty() ? null : dir.resolve(glossary),
                json.path("sourceLanguage").asText("en"),
                json.path("targetLanguage").asText("uk"),
                narrator(json.path("narrator")));
    }

    /** The brief the checks read: the defaults with this book's languages and narrator. */
    BookBrief brief() {
        final BookBrief defaults = BookBrief.defaults(sourceLanguage);
        return new BookBrief(
                defaults.sourceLanguage(),
                targetLanguage,
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                defaults.names(),
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                defaults.alsoTranslate(),
                defaults.dial(),
                narrator);
    }

    private static String required(final JsonNode json, final String field) {
        final String value = json.path(field).asText("");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(FILE + " needs \"" + field + "\"");
        }
        return value;
    }

    private static Narrator narrator(final JsonNode node) {
        if (node.isMissingNode()) {
            return Narrator.unspecified();
        }
        return new Narrator(
                NarratorPerson.valueOf(node.path("person").asText("UNSPECIFIED").toUpperCase(Locale.ROOT)),
                Gender.valueOf(node.path("gender").asText("UNKNOWN").toUpperCase(Locale.ROOT)));
    }
}
