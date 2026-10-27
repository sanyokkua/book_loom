package ua.bookloom.util.paths;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** The label a named project adds to its file name, so two translations of one pair never share a file. */
class ProjectFileLabelTest {

    private static final String AUTO = "Earth Gravity · EN → UK";

    private static Project project(
            final String id,
            final String name,
            final NameOrigin origin,
            final @Nullable String source,
            final @Nullable String target,
            final String hash) {
        final BookBrief brief = BookBrief.defaults(source).withLanguages(source, target);
        return new Project(
                id,
                Path.of("/books/earth-gravity.md"),
                BookFormat.MARKDOWN,
                hash,
                brief,
                name,
                origin,
                "Earth Gravity",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static Project auto() {
        return project("a", AUTO, NameOrigin.AUTO, "en", "uk", "h1");
    }

    private static Project named(final String id, final String name) {
        return project(id, name, NameOrigin.USER, "en", "uk", "h1");
    }

    // IF the automatic project were labelled, THEN its file would no longer be the one the command line writes.
    @Test
    void of_automaticProject_hasNoLabel() {
        final Project self = auto();

        assertThat(ProjectFileLabel.of(self, List.of(self, named("b", "Earth Gravity · 26b max"))))
                .isEmpty();
    }

    // IF a named project shared the plain file, THEN the second translation would replace the first on disk.
    @Test
    void of_namedProjectSharingItsPairWithTheAutomaticOne_keepsTheNameAfterTheBookTitle() {
        final Project self = named("b", "Earth Gravity · 26b max");

        assertThat(ProjectFileLabel.of(self, List.of(auto(), self))).contains("26b-max");
    }

    // IF the automatic name were kept in the label, THEN the file would repeat the language pair.
    @Test
    void of_nameExtendingTheAutomaticName_keepsOnlyTheAddedPart() {
        final Project self = named("b", "Earth Gravity · EN → UK · formal");

        assertThat(ProjectFileLabel.of(self, List.of(auto(), self))).contains("formal");
    }

    // IF a lone named project were labelled, THEN its file would differ from the command line's for no reason.
    @Test
    void of_namedProjectAloneWithItsPair_hasNoLabel() {
        final Project self = named("b", "Earth Gravity · 26b max");

        assertThat(ProjectFileLabel.of(self, List.of(self))).isEmpty();
    }

    // IF only one of two named projects were labelled, THEN the other would still take the plain file.
    @Test
    void of_twoNamedProjectsOfOnePair_bothAreLabelled() {
        final Project first = named("b", "Earth Gravity · formal");
        final Project second = named("c", "Earth Gravity · casual");
        final List<Project> all = List.of(first, second);

        assertThat(ProjectFileLabel.of(first, all)).contains("formal");
        assertThat(ProjectFileLabel.of(second, all)).contains("casual");
    }

    // IF another language pair counted as a clash, THEN a Polish translation would label the Ukrainian one.
    @Test
    void of_otherTargetLanguage_hasNoLabel() {
        final Project self = named("b", "Earth Gravity · 26b max");
        final Project polish = project("p", "Earth Gravity · EN → PL", NameOrigin.AUTO, "en", "pl", "h1");

        assertThat(ProjectFileLabel.of(self, List.of(self, polish))).isEmpty();
    }

    // IF another book with the same pair counted, THEN unrelated books would label each other.
    @Test
    void of_otherBookWithTheSamePair_hasNoLabel() {
        final Project self = named("b", "Earth Gravity · 26b max");
        final Project other = project("o", "Moon Dust · EN → UK", NameOrigin.AUTO, "en", "uk", "h2");

        assertThat(ProjectFileLabel.of(self, List.of(self, other))).isEmpty();
    }

    // IF Auto and a chosen source language were one pair, THEN a translation from English would label an Auto one.
    @Test
    void of_autoSourceLanguageAgainstEnglish_isAnotherPair() {
        final Project self = named("b", "Earth Gravity · 26b max");
        final Project autoSource = project("o", "Earth Gravity · → UK", NameOrigin.AUTO, null, "uk", "h1");

        assertThat(ProjectFileLabel.of(self, List.of(self, autoSource))).isEmpty();
    }

    // IF two Auto-source projects were not compared as equal, THEN a clash between them would go unlabelled.
    @Test
    void of_twoNamedProjectsWithAutoSource_bothAreLabelled() {
        final Project first = project("b", "Earth Gravity · formal", NameOrigin.USER, null, "uk", "h1");
        final Project second = project("c", "Earth Gravity · casual", NameOrigin.USER, null, "uk", "h1");

        assertThat(ProjectFileLabel.of(first, List.of(first, second))).contains("formal");
    }

    // IF the whole name were used when it does not start with the title, THEN the file would still say something.
    @Test
    void of_nameNotStartingWithTheBookTitle_usesTheWholeName() {
        final Project self = named("b", "My Favourite Version");

        assertThat(ProjectFileLabel.of(self, List.of(auto(), self))).contains("my-favourite-version");
    }

    // IF unsafe characters stayed, THEN the label could name a path; letters, digits and hyphens are all that remain.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Earth Gravity · A/B: 26b  max!|ab-26b-max",
                "Earth Gravity · -- Max --|max",
                "Earth Gravity · Українська редакція|українська-редакція",
                "Earth Gravity · UPPER Case|upper-case"
            })
    void of_nameWithUnsafeOrMixedCharacters_isLowerCasedAndReducedToLettersDigitsAndHyphens(
            final String name, final String expected) {
        final Project self = named("b", name);

        assertThat(ProjectFileLabel.of(self, List.of(auto(), self))).contains(expected);
    }

    // IF a name that reduces to nothing gave an empty label, THEN the file would be "earth-gravity.uk..md".
    @Test
    void of_nameThatReducesToNothing_hasNoLabel() {
        final Project self = named("b", "Earth Gravity · !!!");

        assertThat(ProjectFileLabel.of(self, List.of(auto(), self))).isEmpty();
    }
}
