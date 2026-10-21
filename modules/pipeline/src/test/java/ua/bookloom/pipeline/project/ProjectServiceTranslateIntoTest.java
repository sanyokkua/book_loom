package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.TestBooks;

/** Translating a stored book into another language copies what is about the book and nothing about the translation. */
class ProjectServiceTranslateIntoTest {

    @TempDir
    private Path tempDir;

    private ProjectWorld world;
    private String uk;

    @BeforeEach
    void setUp() {
        world = new ProjectWorld();
        final Path book = TestBooks.markdown(
                tempDir.resolve("earth-gravity.md"), "Mira Holt met the warden.\n\nThe rain kept falling.\n");
        uk = world.create(book);
        world.translate(uk, "en", "uk");
        world.glossary.add(
                new GlossaryEntry("g1", uk, "Mira Holt", "Міра Голт", TermType.CHARACTER, Gender.FEMALE, true));
        world.lexicon.put(LexiconEntry.of(uk, "warden"));
        world.lexicon.record(uk, "warden", "наглядач");
        world.tm.put(new TmEntry("t1", uk, "hash", "ctx", "The rain kept falling.", "Дощ не вщухав."));
        world.segments.update(uk, firstSegmentOf(uk), record -> record.withStatus(SegmentStatus.ACCEPTED));
    }

    // IF the target survived, THEN the German project would open showing Ukrainian names it never chose.
    @Test
    void translateInto_glossaryCharacter_comesOverLockedFemaleWithNoTarget() {
        final Project copy = savedProject(world.service.translateInto(uk, "de", null));

        final GlossaryEntry mira =
                Objects.requireNonNull(world.glossary.all(copy.id()).data()).getFirst();
        assertThat(mira.term()).isEqualTo("Mira Holt");
        assertThat(mira.type()).isEqualTo(TermType.CHARACTER);
        assertThat(mira.gender()).isEqualTo(Gender.FEMALE);
        assertThat(mira.locked()).isTrue();
        assertThat(mira.target()).isNull();
    }

    // The Recurring terms keep the term only: a rendering or a choice in Ukrainian means nothing in German.
    @Test
    void translateInto_recurringTerm_keepsNoRenderingsAndNoChoice() {
        final Project copy = savedProject(world.service.translateInto(uk, "de", null));

        final LexiconEntry warden =
                Objects.requireNonNull(world.lexicon.all(copy.id()).data()).getFirst();
        assertThat(warden.term()).isEqualTo("warden");
        assertThat(warden.renderings()).isEmpty();
        assertThat(warden.chosen()).isNull();
    }

    @Test
    void translateInto_newTarget_copiesTheBriefWithTheNewLanguageAndNamesItAutomatically() {
        final Project copy = savedProject(world.service.translateInto(uk, "de", null));

        assertThat(copy.brief().sourceLanguage()).isEqualTo("en");
        assertThat(copy.brief().targetLanguage()).isEqualTo("de");
        assertThat(copy.nameOrigin()).isEqualTo(NameOrigin.AUTO);
        assertThat(copy.name()).endsWith("EN → DE");
        assertThat(copy.source()).isEqualTo(world.stored(uk).source());
    }

    // IF the memory came over, THEN a German draft could reuse a Ukrainian sentence as its own translation.
    @Test
    void translateInto_storedWork_copiesNoTranslationMemoryRunOrDecision() {
        final String copyId =
                savedProject(world.service.translateInto(uk, "de", null)).id();

        assertThat(world.tm.exact(copyId, "hash").data()).isEmpty();
        assertThat(world.tm.candidates(copyId, 0, 1000).data()).isEmpty();
        assertThat(world.runs.latest(copyId).data()).isEmpty();
        final List<SegmentRecord> records =
                Objects.requireNonNull(world.segments.all(copyId).data());
        assertThat(records).hasSameSizeAs(world.segments.all(uk).data());
        assertThat(records).allSatisfy(record -> assertThat(record.status()).isEqualTo(SegmentStatus.PENDING));
    }

    @Test
    void translateInto_sourceProject_isLeftAsItWas() {
        world.service.translateInto(uk, "de", null);

        assertThat(world.glossaryOf(uk).getFirst().target()).isEqualTo("Міра Голт");
        assertThat(world.recordsOf(uk).getFirst().status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void translateInto_pairAlreadyUsedWithAnAutomaticName_isPairInUseAndStoresNothing() {
        final Result<ProjectChange> change = world.service.translateInto(uk, "uk", null);

        assertThat(change.data()).isInstanceOf(ProjectChange.PairInUse.class);
        assertThat(world.storedProjects()).hasSize(1);
    }

    @Test
    void translateInto_pairAlreadyUsedWithATypedName_isSaved() {
        final Project second = savedProject(world.service.translateInto(uk, "uk", "Earth Gravity · 26b"));

        assertThat(second.name()).isEqualTo("Earth Gravity · 26b");
        assertThat(second.nameOrigin()).isEqualTo(NameOrigin.USER);
        assertThat(world.storedProjects()).hasSize(2);
    }

    @Test
    void translateInto_newProject_opensWithoutImportingTheBookAgain() {
        final String copyId =
                savedProject(world.service.translateInto(uk, "de", null)).id();

        final Result<ProjectOpening> opening = world.service.open(copyId);

        assertThat(opening.data()).isInstanceOf(ProjectOpening.Opened.class);
    }

    @Test
    void translateInto_unknownProject_answersValidation() {
        final Result<ProjectChange> change = world.service.translateInto("no-such-project", "de", null);

        assertThat(Objects.requireNonNull(change.error()).code()).isEqualTo(ErrorCode.validation);
    }

    private String firstSegmentOf(final String id) {
        return Objects.requireNonNull(world.segments.all(id).data()).getFirst().segmentId();
    }

    private static Project savedProject(final Result<ProjectChange> result) {
        return ProjectWorld.saved(result);
    }
}
