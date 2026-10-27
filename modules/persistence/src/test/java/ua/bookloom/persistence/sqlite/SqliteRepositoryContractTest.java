package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.persistence.contract.ProjectStorageContractTest;

/**
 * Proves the SQLite adapters against the whole repository contract on a temporary database file with the schema
 * applied, plus what only a stored file can show: the book row, a chunk rolled back part-way, and a restart.
 */
class SqliteRepositoryContractTest extends ProjectStorageContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private final JsonCodec codec = new JsonCodec();
    private SqliteDatabase db;
    private Adapters adapters;

    /** One set of adapters over one open database, so a test can reopen the file and read with new ones. */
    private record Adapters(
            SqliteProjectRepository projects,
            SqliteSegmentRepository segments,
            SqliteGlossaryRepository glossary,
            SqliteTmRepository tm,
            SqliteSummaryRepository summary,
            SqliteDeferralRepository deferrals,
            SqliteRunRepository runs,
            SqliteLexiconRepository lexicon,
            SqliteCheckpoint checkpoint) {

        static Adapters over(final SqliteDatabase db, final JsonCodec codec) {
            return new Adapters(
                    new SqliteProjectRepository(db, codec),
                    new SqliteSegmentRepository(db, codec),
                    new SqliteGlossaryRepository(db, codec),
                    new SqliteTmRepository(db, codec),
                    new SqliteSummaryRepository(db, codec),
                    new SqliteDeferralRepository(db, codec),
                    new SqliteRunRepository(db, codec),
                    new SqliteLexiconRepository(db, codec),
                    new SqliteCheckpoint(db, codec));
        }
    }

    @BeforeEach
    void openDatabase() {
        db = open();
        adapters = Adapters.over(db, codec);
    }

    @AfterEach
    void closeDatabase() {
        db.close();
    }

    private SqliteDatabase open() {
        final Result<SqliteDatabase> opened = SqliteDatabase.open(dir.resolve("bookloom.db"), CLOCK);
        assertThat(opened.isOk()).isTrue();
        return Objects.requireNonNull(opened.data());
    }

    private void restart() {
        db.close();
        db = open();
        adapters = Adapters.over(db, codec);
    }

    private List<String> storedBookPaths() {
        return Objects.requireNonNull(db.read(
                        "test books",
                        h -> h.createQuery("SELECT source_path FROM books ORDER BY source_path")
                                .mapTo(String.class)
                                .list())
                .data());
    }

    @Override
    protected ProjectRepository projectRepository() {
        return adapters.projects();
    }

    @Override
    protected SegmentRepository segmentRepository() {
        return adapters.segments();
    }

    @Override
    protected GlossaryRepository glossaryRepository() {
        return adapters.glossary();
    }

    @Override
    protected TmRepository tmRepository() {
        return adapters.tm();
    }

    @Override
    protected SummaryRepository summaryRepository() {
        return adapters.summary();
    }

    @Override
    protected DeferralRepository deferralRepository() {
        return adapters.deferrals();
    }

    @Override
    protected RunRepository runRepository() {
        return adapters.runs();
    }

    @Override
    protected CheckpointPort checkpointPort() {
        return adapters.checkpoint();
    }

    @Override
    protected LexiconRepository lexiconRepository() {
        return adapters.lexicon();
    }

    @Test
    void delete_lastProjectOfABook_removesTheStoredBookRow() {
        projectRepository().save(project("a", "h1", "One", "en", "uk"));

        projectRepository().delete("a");

        assertThat(storedBookPaths()).isEmpty();
    }

    @Test
    void delete_oneOfTwoProjectsOfABook_keepsTheStoredBookRow() {
        projectRepository().save(project("a", "h1", "One", "en", "uk"));
        projectRepository().save(project("b", "h1", "Two", "en", "de"));

        projectRepository().delete("a");

        assertThat(storedBookPaths()).containsExactly("h1.epub");
    }

    @Test
    void save_sameContentUnderAnotherFileName_storesOneBookRow() {
        projectRepository().save(project("a", "h1", "One", "en", "uk"));
        projectRepository().save(project("b", "h1", "Two", "en", "de"));
        projectRepository().save(project("c", "h2", "One", "en", "uk"));

        assertThat(storedBookPaths()).containsExactly("h1.epub", "h2.epub");
    }

    @Test
    void relocateBook_changesTheStoredPathOfTheBookRow() {
        projectRepository().save(project("a", "h1", "One", "en", "uk"));

        projectRepository().relocateBook("h1", Path.of("moved/book.epub"));

        assertThat(storedBookPaths()).containsExactly("moved/book.epub");
    }

    // A failure after the segments were written (a TM row the database refuses) rolls the segments back too.
    @Test
    void commit_failingPartWay_leavesNoSegmentWritten() {
        givenProjects("p1");
        segmentRepository().saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        db.write(
                "test inject",
                h -> h.execute("CREATE TRIGGER refuse_tm BEFORE INSERT ON tm_entries "
                        + "BEGIN SELECT RAISE(ABORT, 'refused'); END"));
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH).withStatus(SegmentStatus.ACCEPTED)),
                List.of(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт")),
                List.of(),
                List.of());

        final Result<Integer> result = checkpointPort().commit(commit);

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.internal);
        assertThat(Objects.requireNonNull(result.error()).details()).isEqualTo("storage checkpoint commit");
        assertThat(segmentRepository().find("p1", "ch1:0").data())
                .hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(SegmentStatus.PENDING));
    }

    // The run a failed chunk carried must not replace the run stored before it.
    @Test
    void commit_failingPartWay_leavesTheStoredRunUntouched() {
        givenProjects("p1");
        segmentRepository().saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        final Instant started = Instant.parse("2026-10-21T08:00:00Z");
        final RunRecord before = new RunRecord("r1", "p1", started, null, JobState.RUNNING, 0, 0);
        runRepository().save(before);
        db.write(
                "test inject",
                h -> h.execute("CREATE TRIGGER refuse_tm BEFORE INSERT ON tm_entries "
                        + "BEGIN SELECT RAISE(ABORT, 'refused'); END"));
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH).withStatus(SegmentStatus.ACCEPTED)),
                List.of(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт")),
                List.of(),
                List.of(),
                new RunRecord("r1", "p1", started, null, JobState.RUNNING, 1, 0));

        assertThat(checkpointPort().commit(commit).isErr()).isTrue();

        assertThat(runRepository().latest("p1").data()).hasValue(before);
    }

    @Test
    void segmentsOfAProjectNotStored_areRefusedAsAStorageFailure() {
        final Result<Integer> saved =
                segmentRepository().saveAll("ghost", List.of(pending("ghost", "s", "u", 0, SegmentKind.PARAGRAPH)));

        assertThat(saved.isErr()).isTrue();
        assertThat(Objects.requireNonNull(saved.error()).details()).isEqualTo("storage segment saveAll");
    }

    @Test
    void restart_keepsTheWorkTheTermListsAndTheirRemovals() {
        givenProjects("p1");
        segmentRepository().saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        segmentRepository().update("p1", "ch1:0", r -> r.withStatus(SegmentStatus.ACCEPTED));
        tmRepository().put(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт"));
        summaryRepository().save(new RollingSummary("p1", null, "src", "tgt", 3, null, 0));
        glossaryRepository().add(new GlossaryEntry("e1", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));
        glossaryRepository().add(new GlossaryEntry("e2", "p1", "Milton", null, TermType.PLACE, Gender.NEUTER, false));
        glossaryRepository().remove("p1", "e2");
        lexiconRepository().record("p1", "warden", "наглядач");
        lexiconRepository().put(LexiconEntry.of("p1", "master"));
        lexiconRepository().remove("p1", "master");

        restart();

        assertThat(Objects.requireNonNull(segmentRepository()
                                .countsByStatus("p1", Set.of())
                                .data())
                        .accepted())
                .isEqualTo(1);
        assertThat(tmRepository().context("p1", "h1", "c1").data()).isPresent();
        assertThat(summaryRepository().latest("p1").data())
                .hasValueSatisfying(s -> assertThat(s.version()).isEqualTo(3));
        assertThat(glossaryRepository().all("p1").data())
                .extracting(GlossaryEntry::term)
                .containsExactly("Hale");
        assertThat(glossaryRepository().wasRemoved("p1", "milton").data()).isTrue();
        assertThat(lexiconRepository().all("p1").data())
                .extracting(LexiconEntry::term)
                .containsExactly("warden");
        assertThat(lexiconRepository().wasRemoved("p1", "Master").data()).isTrue();
    }
}
