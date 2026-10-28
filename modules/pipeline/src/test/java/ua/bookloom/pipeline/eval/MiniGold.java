package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Gender;

/** The gold map of a dataset, {@code eval/datasets/<id>/gold-map.json}: the lemmas a later grader counts against. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MiniGold {

    /**
     * One gold lemma.
     *
     * @param lemma the name as the glossary files it
     * @param forms the forms of it that occur in the book
     * @param target the rendering the case author wants; may carry a stress mark when it is a seed
     * @param gender the gender the book proves, unknown when it proves none
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String lemma, List<String> forms, String target, Gender gender) {

        /** Copies the forms. */
        Entry {
            Objects.requireNonNull(lemma, "lemma");
            forms = List.copyOf(forms);
        }
    }

    static List<Entry> load(final String dataset) {
        final String resource = "/eval/datasets/" + dataset + "/gold-map.json";
        try (InputStream in = MiniGold.class.getResourceAsStream(resource)) {
            return JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                    .build()
                    .readValue(Objects.requireNonNull(in, resource), new TypeReference<List<Entry>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
