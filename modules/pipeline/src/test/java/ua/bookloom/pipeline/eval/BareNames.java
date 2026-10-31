package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;

/**
 * The invented bare names of arm 4a ({@code eval/datasets/bare-names/names.json}): each with the language it is
 * written in, its gold gender and whether it is unisex. A unisex name has no gold gender.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BareNames {

    static final String DATASET = "bare-names";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One bare name.
     *
     * @param term the name as asked
     * @param language the tag of the language the name is written in
     * @param gender the gold gender; null for a unisex name
     * @param unisex whether the name is used for both men and women, so only a low confidence is right
     */
    record BareName(String term, String language, @Nullable Gender gender, boolean unisex) {

        /** Rejects a gendered name with no gold gender and a unisex name with one. */
        BareName {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(language, "language");
            if (unisex == (gender != null)) {
                throw new IllegalArgumentException("a unisex name has no gold gender, any other one has: " + term);
            }
        }
    }

    /** The committed names, in file order. */
    static List<BareName> load() {
        final String resource = "/eval/datasets/" + DATASET + "/names.json";
        try (InputStream in = BareNames.class.getResourceAsStream(resource)) {
            final JsonNode names =
                    MAPPER.readTree(Objects.requireNonNull(in, resource)).get("names");
            final List<BareName> loaded =
                    names.valueStream().map(BareNames::nameOf).toList();
            log.debug("Bare names loaded {}", loaded.size());
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BareName nameOf(final JsonNode node) {
        final JsonNode gender = node.get("gender");
        return new BareName(
                node.get("term").asText(),
                node.get("language").asText(),
                gender.isNull() ? null : Gender.valueOf(gender.asText().toUpperCase(Locale.ROOT)),
                node.get("unisex").asBoolean());
    }
}
