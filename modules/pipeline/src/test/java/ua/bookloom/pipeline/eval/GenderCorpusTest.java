package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.checks.GenderChecks;

/**
 * The narrator-gender check against the 15d.10 gender cases, with no model: every defective candidate is caught and no
 * more than one in twenty faithful ones is flagged — the number the task's done-when names.
 */
class GenderCorpusTest {

    private static final double MAX_FALSE_POSITIVE_RATE = 0.05;

    /** One labelled case: the candidate, the narrator it is read against, and whether it is wrong. */
    record GenderCase(String id, String narrator, String candidate, boolean defective, String rationale) {}

    private static List<GenderCase> cases() {
        try (InputStream in = GenderCorpusTest.class.getResourceAsStream("/eval/gender.json")) {
            Objects.requireNonNull(in, "gender.json");
            return new ObjectMapper().readValue(in, new TypeReference<List<GenderCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean flagged(final GenderCase corpusCase) {
        return !GenderChecks.named("uk")
                .find(corpusCase.candidate(), Gender.valueOf(corpusCase.narrator()))
                .isEmpty();
    }

    @Test
    void genderCheck_defectiveCases_areAllCaught() {
        final List<GenderCase> defective =
                cases().stream().filter(GenderCase::defective).toList();

        assertThat(defective).hasSizeGreaterThanOrEqualTo(10);
        assertThat(defective.stream().filter(corpusCase -> !flagged(corpusCase)).map(GenderCase::id))
                .isEmpty();
    }

    @Test
    void genderCheck_faithfulCases_areFlaggedAtMostFivePercent() {
        final List<GenderCase> faithful =
                cases().stream().filter(corpusCase -> !corpusCase.defective()).toList();
        final List<String> falseAlarms = faithful.stream()
                .filter(GenderCorpusTest::flagged)
                .map(GenderCase::id)
                .toList();

        assertThat(faithful).hasSizeGreaterThanOrEqualTo(20);
        assertThat((double) falseAlarms.size() / faithful.size()).isLessThanOrEqualTo(MAX_FALSE_POSITIVE_RATE);
    }
}
