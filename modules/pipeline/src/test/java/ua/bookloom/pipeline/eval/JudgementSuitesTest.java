package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.pipeline.eval.JudgementReport.Item;

/**
 * The stability and gold suites (15h.E3) proven offline: a fake model that answers each fixture book as its gold says,
 * and one that changes its answer under one seed, scored by the same report the real runs write.
 */
class JudgementSuitesTest {

    private static final String BOOK = "gender-late-or-object";

    @TempDir
    private Path dir;

    private static Item item(final JudgementReport report, final String name) {
        return report.books().getFirst().items().stream()
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void stability_modelThatAnswersAlike_agreesOnEveryFieldUnderThreeSeeds() {
        final JudgementBook book = JudgementBook.byId(BOOK);

        final JudgementReport report = JudgementSuites.stability(
                "fake", new JudgementFakeModel(book, Set.of()), dir, Map.of(JudgementSuites.BOOK_ENV, BOOK));

        assertThat(report.seeds()).containsExactly(1, 2, 3);
        assertThat(report.books()).extracting(JudgementReport.Book::book).containsExactly(BOOK);
        assertThat(report.agreement()).isEqualTo(1.0);
        assertThat(report.accuracy()).isEqualTo(1.0);
        assertThat(report.belowTarget()).isEmpty();
        assertThat(item(report, "Odile:gender").answers()).containsExactly("FEMALE", "FEMALE", "FEMALE");
    }

    // A field the second seed answers otherwise agrees at 2/3, below the 0.8 target; a gender the book's pronouns
    // decide is the code's, so the model's other answer cannot move it.
    @Test
    void stability_modelThatChangesItsAnswerUnderOneSeed_reportsTwoThirdsBelowTheTarget() {
        final JudgementBook book = JudgementBook.byId(BOOK);

        final JudgementReport report = JudgementSuites.stability(
                "fake", new JudgementFakeModel(book, Set.of(2)), dir, Map.of(JudgementSuites.BOOK_ENV, BOOK));

        assertThat(item(report, "register").answers()).containsExactly("NEUTRAL", "CASUAL", "NEUTRAL");
        assertThat(item(report, "register").agreement()).isEqualTo(2.0 / 3);
        assertThat(item(report, "Odile:gender").answers()).containsExactly("FEMALE", "MALE", "FEMALE");
        assertThat(item(report, "Bram:gender").answers()).containsExactly("MALE", "MALE", "MALE");
        assertThat(report.belowTarget()).containsExactly(BOOK + ":register", BOOK + ":Odile:gender");
        assertThat(report.json())
                .startsWith("{\"suite\":\"stability\",\"model\":\"fake\",\"target\":0.8,\"agreement\":0.667,")
                .contains("\"meetsTarget\":false", "\"seeds\":[1,2,3]")
                .contains("{\"name\":\"register\",\"gold\":\"NEUTRAL\",\"answers\":[\"NEUTRAL\",\"CASUAL\","
                        + "\"NEUTRAL\"],\"agreement\":0.667,\"accuracy\":0.667}");
        assertThat(report.table()).contains("promptEval stability model=fake", "register", "agree=0.67");
    }

    @Test
    void gold_ownerDirectory_scoresEachGoldFileAgainstTheBookItNames() throws IOException {
        final JudgementBook book = JudgementBook.byId("female-narrator-address");
        final Path written = book.write(dir);
        Files.writeString(dir.resolve("house.gold.json"), """
                {"book": "%s", "sourceLanguage": "en", "targetLanguage": "uk",
                 "brief": {"genre": "gothic novel", "genreClass": ["gothic", "literary", "fiction"],
                           "register": "NEUTRAL", "narrator": "FIRST", "narratorGender": "FEMALE"},
                 "characters": [{"term": "Tobias", "type": "CHARACTER", "gender": "MALE"}]}
                """.formatted(written.getFileName()));
        Files.writeString(dir.resolve("notes.json"), "{}");

        final JudgementReport report =
                JudgementSuites.gold("fake", new JudgementFakeModel(book, Set.of()), dir, Map.of());

        assertThat(report.suite()).isEqualTo("gold");
        assertThat(report.seeds()).containsExactly(1);
        assertThat(report.books()).extracting(JudgementReport.Book::book).containsExactly("house");
        assertThat(report.books().getFirst().items())
                .extracting(Item::name)
                .containsExactly("genre", "register", "narrator", "narratorGender", "Tobias:type", "Tobias:gender");
        assertThat(report.accuracy()).isEqualTo(1.0);
    }

    @Test
    void run_goldWithNoCorpusDirectory_failsNamingTheVariable() {
        final JudgementBook book = JudgementBook.byId(BOOK);

        assertThatThrownBy(() ->
                        JudgementSuites.run("gold", "fake", new JudgementFakeModel(book, Set.of()), dir, Map.of()))
                .hasMessageContaining(JudgementSuites.CORPUS_ENV);
    }

    @Test
    void books_all_givesEveryCommittedBookAndAnUnknownIdFailsNamingThem() {
        assertThat(JudgementSuites.books("all")).hasSize(6);
        assertThat(JudgementSuites.books(null)).extracting(JudgementBook::id).containsExactly(JudgementBook.DEFAULT_ID);
        assertThatThrownBy(() -> JudgementSuites.books("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vocative-name");
    }

    @Test
    void seeds_commaList_isReadAndUnsetGivesTheFallback() {
        assertThat(JudgementSuites.seeds(" 7, 11 ,13", List.of(1))).containsExactly(7, 11, 13);
        assertThat(JudgementSuites.seeds("", List.of(1, 2, 3))).containsExactly(1, 2, 3);
    }

    @Test
    void genreAnswer_genreHoldingAClassWord_isThatWordAndAnyOtherIsItself() {
        assertThat(JudgementReport.genreAnswer(List.of("cyberpunk", "science fiction"), "Dark Cyberpunk Stories"))
                .isEqualTo("cyberpunk");
        assertThat(JudgementReport.genreAnswer(List.of("cyberpunk"), "Western")).isEqualTo("western");
        assertThat(JudgementReport.genreAnswer(List.of("cyberpunk"), null)).isEqualTo("-");
    }
}
