package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.StageCases.SetupCase;

/**
 * The setup suite's brief cases for the new prediction (15i.E3) load, are invented or public domain, and carry the gold
 * and the knowledge expectation each field is scored on: eight invented title and author pairs, four public-domain
 * classics, a book with no metadata, besides the two cases of 15h.E3.
 */
class SetupCasesTest {

    @TempDir
    private Path dir;

    static Stream<SetupCase> cases() {
        return StageCases.setup().stream();
    }

    static Stream<SetupCase> classics() {
        return cases().filter(SetupCase::classic);
    }

    private static List<SetupCase> invented() {
        return StageCases.setup().stream()
                .filter(setupCase -> setupCase.id().startsWith("invented-"))
                .toList();
    }

    private Path write(final SetupCase setupCase) {
        return Fb2Fixture.write(
                dir,
                new Fb2Fixture.Meta(
                        setupCase.fileStem(),
                        setupCase.title(),
                        setupCase.author(),
                        setupCase.source(),
                        setupCase.year(),
                        setupCase.publisher()),
                List.of(new Fb2Fixture.Section(null, setupCase.paragraphs())));
    }

    private static Prepared imported(final SetupCase setupCase, final Path book) {
        return SequenceJobs.importBook(
                book,
                SequenceJobs.brief(setupCase.source(), setupCase.target(), QualityDial.FAST, Narrator.unspecified()));
    }

    @Test
    void setup_cases_holdTheTwoOldCasesTheInventedPairsTheClassicsAndTheBookWithNoMetadata() {
        assertThat(StageCases.setup())
                .extracting(SetupCase::id)
                .containsExactly(
                        "setup-en-uk-first-person",
                        "setup-ru-uk-third-person",
                        "invented-01-lantern-ledger",
                        "invented-02-small-clocks",
                        "invented-03-salt-road",
                        "invented-04-quiet-engine",
                        "invented-05-borrowed-garden",
                        "invented-06-thirteenth-weir",
                        "invented-07-paper-moon",
                        "invented-08-watchmaker",
                        "classic-pride-and-prejudice",
                        "classic-moby-dick",
                        "classic-tom-sawyer",
                        "classic-dracula",
                        "setup-no-metadata");
    }

    // IF an invented pair repeated or were also a real book, THEN "unknown" would be the wrong thing to hold the model
    // to.
    @Test
    void invented_eightPairs_areDistinctAndAreExpectedToBeUnknown() {
        assertThat(invented()).hasSize(8);
        assertThat(invented()).allSatisfy(setupCase -> {
            assertThat(setupCase.classic()).isFalse();
            assertThat(setupCase.expectKnowledge()).isEqualTo("unknown");
            assertThat(setupCase.title()).isNotBlank();
            assertThat(setupCase.author()).isNotBlank();
        });
        assertThat(invented().stream()
                        .map(setupCase -> setupCase.title() + "/" + setupCase.author())
                        .collect(Collectors.toSet()))
                .hasSize(8);
    }

    @Test
    void classics_fourPublicDomainBooks_arePre1929WithTitleAuthorAndYearAndExpectedKnown() {
        assertThat(classics().toList())
                .extracting(SetupCase::title, SetupCase::author, SetupCase::year, SetupCase::expectKnowledge)
                .containsExactly(
                        tuple("Pride and Prejudice", "Jane Austen", 1813, "known"),
                        tuple("Moby-Dick; or, The Whale", "Herman Melville", 1851, "known"),
                        tuple("The Adventures of Tom Sawyer", "Mark Twain", 1876, "known"),
                        tuple("Dracula", "Bram Stoker", 1897, "known"));
    }

    // IF the gold called a third-person narration casual, THEN a model that rightly reads it as neutral would be scored
    // wrong on the register.
    @Test
    void classics_tomSawyer_isThirdPersonWithANeutralRegister() {
        final SetupCase tom = StageCases.setup().stream()
                .filter(setupCase -> setupCase.id().equals("classic-tom-sawyer"))
                .findFirst()
                .orElseThrow();

        assertThat(tom.narrator()).isEqualTo(NarratorPerson.THIRD);
        assertThat(tom.register()).isEqualTo(Register.NEUTRAL);
    }

    @Test
    void noMetadataCase_hasNoTitleNoAuthorNoYearAndIsExpectedUnknown() {
        final SetupCase none = StageCases.setup().getLast();

        assertThat(none.id()).isEqualTo("setup-no-metadata");
        assertThat(none.hasTitle()).isFalse();
        assertThat(none.author()).isEmpty();
        assertThat(none.year()).isNull();
        assertThat(none.expectKnowledge()).isEqualTo("unknown");
    }

    // Every case states a gold for the fields the report scores: genre class, register, narrator and its gender.
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void cases_everyCase_haveAGoldForGenreRegisterNarratorAndGender(final SetupCase setupCase) {
        assertThat(setupCase.genreClass()).isNotEmpty();
        assertThat(setupCase.register()).isNotNull();
        assertThat(setupCase.narrator()).isNotNull();
        assertThat(setupCase.narratorGender()).isNotNull();
        assertThat(setupCase.paragraphs()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void setupCase_expectationThatIsNeitherKnownNorUnknown_isRejected() {
        final SetupCase any = StageCases.setup().getFirst();

        assertThatThrownBy(() -> new SetupCase(
                        any.id(),
                        any.source(),
                        any.target(),
                        any.fileStem(),
                        any.title(),
                        any.author(),
                        null,
                        null,
                        false,
                        "maybe",
                        any.paragraphs(),
                        any.narrator(),
                        any.narratorGender(),
                        any.register(),
                        any.genreClass()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maybe");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("classics")
    void fixture_classic_writesTheYearTheTitleAndTheAuthorIntoTheFile(final SetupCase classic) throws IOException {
        final String fb2 = Files.readString(write(classic));

        assertThat(fb2)
                .contains("<date>" + classic.year() + "</date>")
                .contains("<book-title>")
                .contains("<first-name>");
    }

    @Test
    void fixture_inventedCaseWithAPublisher_writesItIntoThePublishInfo() throws IOException {
        final SetupCase ledger = StageCases.setup().get(2);

        assertThat(Files.readString(write(ledger)))
                .contains("<publisher>Harrowgate Press</publisher>")
                .contains("<date>2014</date>");
    }

    @Test
    void importBook_caseWithNoMetadata_hasNoTitleOrAuthorInTheOpenedBook() {
        final SetupCase none = StageCases.setup().getLast();

        final Prepared prepared = imported(none, write(none));

        assertThat(prepared.document().metadata()).doesNotContainKey("title").doesNotContainKey("author");
    }

    @Test
    void importBook_classicWithTitleAndAuthor_hasThemInTheOpenedBook() {
        final SetupCase classic = StageCases.setup().get(10);

        final Prepared prepared = imported(classic, write(classic));

        assertThat(prepared.document().metadata())
                .containsEntry("title", "Pride and Prejudice")
                .containsEntry("author", "Jane Austen");
    }
}
