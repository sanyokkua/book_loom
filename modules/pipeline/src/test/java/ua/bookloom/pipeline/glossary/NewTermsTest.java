package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;

/** The proposals that survive the glossary the person already holds and the names they removed. */
class NewTermsTest {

    private static final String PROJECT = "p1";

    private GlossaryRepository glossary;

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
    }

    @Test
    void newTerms_emptyGlossary_proposesUnlockedTargetlessOtherEntriesAndWritesNothing() {
        final List<GlossaryEntry> entries = newTermsOf(namesBook());

        assertThat(entries)
                .containsExactly(
                        new GlossaryEntry("p1:hale", PROJECT, "Hale", null, TermType.OTHER, Gender.UNKNOWN, false),
                        new GlossaryEntry(
                                "p1:baker street",
                                PROJECT,
                                "Baker Street",
                                null,
                                TermType.OTHER,
                                Gender.UNKNOWN,
                                false));
        assertThat(glossary.all(PROJECT).data()).isEmpty();
    }

    @Test
    void newTerms_nameSeenInTwoCaseVariants_proposesOnlyTheFirstRankedVariant() {
        final List<String> lines = new ArrayList<>(copies("We saw Hale at the door.", 3));
        lines.addAll(copies("We saw HALE at the door.", 3));

        final List<GlossaryEntry> entries = newTermsOf(GlossaryTestSegments.of(lines));

        assertThat(entries).extracting(GlossaryEntry::term).containsExactly("Hale");
    }

    @Test
    void newTerms_personHoldsTheTermInAnotherCase_doesNotProposeIt() {
        glossary.add(new GlossaryEntry("e1", PROJECT, "hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));

        final List<GlossaryEntry> entries = newTermsOf(GlossaryTestSegments.of(copies("We saw Hale at the door.", 5)));

        assertThat(entries).isEmpty();
    }

    @Test
    void newTerms_personRemovedTheTerm_doesNotProposeItAgain() {
        glossary.add(new GlossaryEntry("e2", PROJECT, "Chapter", null, TermType.TERM, Gender.NEUTER, false));
        glossary.remove(PROJECT, "e2");

        final List<GlossaryEntry> entries =
                newTermsOf(GlossaryTestSegments.of(copies("We read Chapter and Hale aloud.", 5)));

        assertThat(entries).extracting(GlossaryEntry::term).containsExactly("Hale");
    }

    @Test
    void newTerms_glossaryReadFails_returnsThatError() {
        final AppError failure = AppError.of(ErrorCode.internal, "Read failed", "The glossary could not be read.");

        final Result<List<GlossaryEntry>> result =
                FrequencyScan.newTerms(PROJECT, namesBook(), new FailingGlossary(failure, null));

        assertThat(result.error()).isEqualTo(failure);
    }

    @Test
    void newTerms_removalLookupFails_returnsThatError() {
        final AppError failure = AppError.of(ErrorCode.internal, "Read failed", "The removals could not be read.");

        final Result<List<GlossaryEntry>> result =
                FrequencyScan.newTerms(PROJECT, namesBook(), new FailingGlossary(null, failure));

        assertThat(result.error()).isEqualTo(failure);
    }

    @ParameterizedTest
    @CsvSource({"p1,Hale,p1:hale", "p2,Baker Street,p2:baker street", "p1,Élan,p1:élan"})
    void of_projectAndTerm_isProjectColonNfcLowerCaseTerm(final String project, final String term, final String id) {
        assertThat(GlossaryIds.of(project, term)).isEqualTo(id);
    }

    private List<GlossaryEntry> newTermsOf(final List<Segment> segments) {
        final Result<List<GlossaryEntry>> result = FrequencyScan.newTerms(PROJECT, segments, glossary);
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private static List<Segment> namesBook() {
        final List<String> lines = new ArrayList<>(copies("We saw Hale at the door.", 5));
        lines.addAll(copies("They walked to Baker Street later.", 3));
        return GlossaryTestSegments.of(lines);
    }

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    /** A glossary whose read of the entries, or of the removal memory, answers a chosen error. */
    private record FailingGlossary(
            @Nullable AppError allError, @Nullable AppError removedError) implements GlossaryRepository {

        @Override
        public Result<List<GlossaryEntry>> all(final String projectId) {
            return allError == null ? Result.ok(List.of()) : Result.err(allError);
        }

        @Override
        public Result<Boolean> wasRemoved(final String projectId, final String term) {
            return removedError == null ? Result.ok(false) : Result.err(removedError);
        }

        @Override
        public Result<GlossaryEntry> add(final GlossaryEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<GlossaryEntry> update(final GlossaryEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Boolean> remove(final String projectId, final String entryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Optional<GlossaryEntry>> findByTerm(final String projectId, final String term) {
            throw new UnsupportedOperationException();
        }
    }
}
