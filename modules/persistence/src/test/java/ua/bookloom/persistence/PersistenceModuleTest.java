package ua.bookloom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.persistence.StorageInfo;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.persistence.sqlite.SqliteDatabase;

/**
 * Proves the injector really resolves every storage port from {@link InMemoryPersistenceModule} and {@link SqlitePersistenceModule}, each a singleton, and that
 * every adapter shares one underlying store (a segment saved through {@link SegmentRepository} is visible to a
 * {@link CheckpointPort} commit's validation).
 */
class PersistenceModuleTest {

    @TempDir
    Path dir;

    private final Injector injector = Guice.createInjector(new InMemoryPersistenceModule());

    @Test
    void injector_resolvesEveryStoragePort() {
        assertThat(injector.getInstance(ProjectRepository.class)).isNotNull();
        assertThat(injector.getInstance(SegmentRepository.class)).isNotNull();
        assertThat(injector.getInstance(GlossaryRepository.class)).isNotNull();
        assertThat(injector.getInstance(TmRepository.class)).isNotNull();
        assertThat(injector.getInstance(SummaryRepository.class)).isNotNull();
        assertThat(injector.getInstance(DeferralRepository.class)).isNotNull();
        assertThat(injector.getInstance(LexiconRepository.class)).isNotNull();
        assertThat(injector.getInstance(RunRepository.class)).isNotNull();
        assertThat(injector.getInstance(CheckpointPort.class)).isNotNull();
        assertThat(injector.getInstance(SettingsRepository.class)).isNotNull();
        assertThat(injector.getInstance(ModelSettingsRepository.class)).isNotNull();
        assertThat(injector.getInstance(StorageInfo.class)).isNotNull();
    }

    @Test
    void injector_answersOneSettingsRepositoryForEveryLookup() {
        assertThat(injector.getInstance(SettingsRepository.class))
                .isSameAs(injector.getInstance(SettingsRepository.class));
    }

    @Test
    void injector_answersOneModelSettingsRepositoryForEveryLookup() {
        assertThat(injector.getInstance(ModelSettingsRepository.class))
                .isSameAs(injector.getInstance(ModelSettingsRepository.class));
    }

    @Test
    void injector_answersTheSameInstanceOnASecondLookup() {
        assertThat(injector.getInstance(SegmentRepository.class))
                .isSameAs(injector.getInstance(SegmentRepository.class));
    }

    @Test
    void segmentSavedThroughSegmentRepository_isVisibleToACheckpointCommit() {
        final SegmentRepository segments = injector.getInstance(SegmentRepository.class);
        final SegmentRecord record = new SegmentRecord(
                "p1",
                "ch1:0",
                "ch1",
                0,
                SegmentKind.PARAGRAPH,
                SegmentStatus.PENDING,
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
        segments.saveAll("p1", List.of(record));

        final CheckpointPort checkpoint = injector.getInstance(CheckpointPort.class);
        final Result<Integer> result = checkpoint.commit(new ChunkCommit(
                "p1", List.of(record.withStatus(SegmentStatus.ACCEPTED)), List.of(), List.of(), List.of()));

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void inMemory_providerConfigs_hasThePresets() {
        assertThat(injector.getInstance(ProviderConfigs.class).all()).isNotEmpty();
    }

    @Test
    void sqlite_resolvesEveryStoragePortAsSingletons() {
        final Injector sqlite = sqliteInjector();
        assertThat(List.<Class<?>>of(
                        ProjectRepository.class,
                        SegmentRepository.class,
                        GlossaryRepository.class,
                        TmRepository.class,
                        SummaryRepository.class,
                        DeferralRepository.class,
                        LexiconRepository.class,
                        RunRepository.class,
                        CheckpointPort.class,
                        SettingsRepository.class,
                        ModelSettingsRepository.class,
                        ProviderConfigs.class,
                        StorageInfo.class))
                .allSatisfy(port -> assertThat(sqlite.getInstance(port)).isSameAs(sqlite.getInstance(port)));
    }

    @Test
    void sqlite_providerConfigs_startsEmptyAndStorageInfoNamesTheFile() {
        final Injector sqlite = sqliteInjector();

        assertThat(sqlite.getInstance(ProviderConfigs.class).all()).isEmpty();
        assertThat(sqlite.getInstance(StorageInfo.class).location()).contains(dir.resolve("bookloom.db"));
    }

    @Test
    void sqlite_adapters_shareOneDatabase() {
        final Injector sqlite = sqliteInjector();

        assertThat(sqlite.getInstance(SqliteDatabase.class)).isSameAs(sqlite.getInstance(SqliteDatabase.class));
    }

    @Test
    void sqlite_unopenableFolder_failsWhenTheDatabaseIsFirstUsed() {
        final Injector broken = Guice.createInjector(
                new SqlitePersistenceModule(dir.resolve("missing").resolve("bookloom.db"), Clock.systemUTC()));

        assertThatThrownBy(() -> broken.getInstance(SqliteDatabase.class)).isInstanceOf(ProvisionException.class);
    }

    // IF the typed failure were lost in Guice's wrapping, THEN the window could only crash instead of telling the
    // person.
    @Test
    void sqlite_unopenableFolder_carriesTheTypedFailureForTheStartupMessage() {
        final Injector broken = Guice.createInjector(
                new SqlitePersistenceModule(dir.resolve("missing").resolve("bookloom.db"), Clock.systemUTC()));

        assertThatThrownBy(() -> broken.getInstance(SqliteDatabase.class))
                .satisfies(thrown -> assertThat(StorageOpenException.errorIn(thrown))
                        .hasValueSatisfying(error -> assertThat(error.title()).isEqualTo("Storage not writable")));
    }

    @Test
    void errorIn_unrelatedFailure_isEmpty() {
        assertThat(StorageOpenException.errorIn(new IllegalStateException("other")))
                .isEmpty();
    }

    private Injector sqliteInjector() {
        return Guice.createInjector(new SqlitePersistenceModule(dir.resolve("bookloom.db"), Clock.systemUTC()));
    }
}
