package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** The automatic name, the title it starts with and the clashes inside one book; no repository is involved. */
class ProjectNamesTest {

    private static final String BOOK = "hash-earth";

    static Stream<Arguments> automaticNames() {
        return Stream.of(
                Arguments.of("Earth Gravity", "en", "uk", "Earth Gravity · EN → UK"),
                Arguments.of("Earth Gravity", null, "uk", "Earth Gravity · → UK"),
                Arguments.of("Earth Gravity", "en", null, "Earth Gravity · EN"),
                Arguments.of("Earth Gravity", null, null, "Earth Gravity"),
                Arguments.of("Earth Gravity", "en-US", "uk-UA", "Earth Gravity · EN → UK"),
                Arguments.of("Земля", "uk", "de", "Земля · UK → DE"));
    }

    // IF the languages are absent or carry a region, THEN the name still shows the primary subtag in capitals only.
    @ParameterizedTest
    @MethodSource("automaticNames")
    void automatic_languagesGiven_buildsTitleSourceArrowTarget(
            final String title, final @Nullable String source, final @Nullable String target, final String expected) {
        assertThat(ProjectNames.automatic(title, source, target)).isEqualTo(expected);
    }

    static Stream<Arguments> titles() {
        return Stream.of(
                Arguments.of("Earth Gravity: A Novel of Velk", "earth-gravity.epub", "Earth Gravity"),
                Arguments.of("  Earth Gravity  ", "earth-gravity.epub", "Earth Gravity"),
                Arguments.of(null, "earth-gravity.epub", "earth-gravity"),
                Arguments.of("   ", "earth-gravity.epub", "earth-gravity"),
                Arguments.of(": A Novel", "earth-gravity.epub", "earth-gravity"),
                Arguments.of(null, "notes", "notes"));
    }

    // IF the metadata title is missing or blank before its first colon, THEN the file name without extension is used.
    @ParameterizedTest
    @MethodSource("titles")
    void titleOf_metadataTitleOrFileName_cutsAtTheFirstColon(
            final @Nullable String metadata, final String fileName, final String expected) {
        assertThat(ProjectNames.titleOf(metadata, Path.of("/books", fileName))).isEqualTo(expected);
    }

    @Test
    void conflictOf_pairUsedByAnotherProjectOfTheBook_answersPairInUseNamingIt() {
        final Project existing = project("a", BOOK, "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");
        final Project candidate = project("b", BOOK, "Earth Gravity · EN → UK (new)", NameOrigin.AUTO, "en", "uk");

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing)))
                .containsInstanceOf(ProjectChange.PairInUse.class)
                .hasValueSatisfying(change -> assertThat(
                                ((ProjectChange.PairInUse) change).existing().id())
                        .isEqualTo("a"));
    }

    @Test
    void conflictOf_namedProjectOnAUsedPairWithItsOwnName_isAccepted() {
        final Project existing = project("a", BOOK, "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");
        final Project candidate = project("b", BOOK, "Earth Gravity · 26b", NameOrigin.USER, "en", "uk");

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing))).isEmpty();
    }

    @Test
    void conflictOf_sameNameTwiceInOneBook_answersNameInUseNamingTheHolder() {
        final Project existing = project("a", BOOK, "Earth Gravity · 26b", NameOrigin.USER, "en", "uk");
        final Project candidate = project("b", BOOK, "Earth Gravity · 26b", NameOrigin.USER, "en", "uk");

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing)))
                .hasValueSatisfying(change -> assertThat(change).isEqualTo(new ProjectChange.NameInUse(existing)));
    }

    @Test
    void conflictOf_sameAutomaticNameInTwoBooks_isAccepted() {
        final Project other = project("a", "hash-other", "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");
        final Project candidate = project("b", BOOK, "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");

        assertThat(ProjectNames.conflictOf(candidate, List.of(other))).isEmpty();
    }

    @Test
    void conflictOf_cyrillicNamesDifferingByCase_answersNameInUse() {
        final Project existing = project("a", BOOK, "Земля", NameOrigin.USER, "en", "uk");
        final Project candidate = project("b", BOOK, "ЗЕМЛЯ", NameOrigin.USER, "en", "de");

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing)))
                .containsInstanceOf(ProjectChange.NameInUse.class);
    }

    @Test
    void conflictOf_nameDifferingByCaseAndSurroundingSpaces_answersNameInUse() {
        final Project existing = project("a", BOOK, "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");
        final Project candidate = project("b", BOOK, "  earth gravity · en → uk ", NameOrigin.USER, "en", "de");

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing)))
                .containsInstanceOf(ProjectChange.NameInUse.class);
    }

    // IF a named project returns to its automatic name while another project holds the pair, THEN it is refused.
    @Test
    void conflictOf_automaticCandidateWhileAnotherProjectHoldsThePair_answersPairInUse() {
        final Project existing = project("a", BOOK, "Earth Gravity · EN → DE", NameOrigin.AUTO, "en", "de");
        final Project candidate = project("b", BOOK, "Earth Gravity · EN → DE", NameOrigin.AUTO, "en", "de");

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing)))
                .containsInstanceOf(ProjectChange.PairInUse.class);
    }

    @Test
    void conflictOf_candidateAlsoStored_ignoresItsOwnRow() {
        final Project stored = project("a", BOOK, "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");

        assertThat(ProjectNames.conflictOf(stored, List.of(stored))).isEmpty();
    }

    @Test
    void conflictOf_noTargetCandidateWithTheSameNameKey_answersNameInUse() {
        final Project existing = project("a", BOOK, "Earth Gravity · EN → …", NameOrigin.AUTO, "en", null);
        final Project candidate = project("b", BOOK, "Earth Gravity · EN → … ", NameOrigin.USER, "en", null);

        assertThat(ProjectNames.conflictOf(candidate, List.of(existing)))
                .containsInstanceOf(ProjectChange.NameInUse.class);
    }

    @Test
    void conflictOf_noOtherProject_isEmpty() {
        final Project candidate = project("a", BOOK, "Earth Gravity · EN → UK", NameOrigin.AUTO, "en", "uk");

        assertThat(ProjectNames.conflictOf(candidate, List.of())).isEmpty();
    }

    private static Project project(
            final String id,
            final String hash,
            final String name,
            final NameOrigin origin,
            final @Nullable String source,
            final @Nullable String target) {
        return new Project(
                id,
                Path.of("/books/earth-gravity.epub"),
                BookFormat.EPUB,
                hash,
                BookBrief.defaults(source).withLanguages(source, target),
                name,
                origin,
                "Earth Gravity",
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
