package ua.bookloom.persistence;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
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
import ua.bookloom.persistence.sqlite.SqliteCheckpoint;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.persistence.sqlite.SqliteDeferralRepository;
import ua.bookloom.persistence.sqlite.SqliteGlossaryRepository;
import ua.bookloom.persistence.sqlite.SqliteLexiconRepository;
import ua.bookloom.persistence.sqlite.SqliteModelSettingsRepository;
import ua.bookloom.persistence.sqlite.SqliteProjectRepository;
import ua.bookloom.persistence.sqlite.SqliteProviderConfigs;
import ua.bookloom.persistence.sqlite.SqliteRunRepository;
import ua.bookloom.persistence.sqlite.SqliteSegmentRepository;
import ua.bookloom.persistence.sqlite.SqliteSettingsRepository;
import ua.bookloom.persistence.sqlite.SqliteStorageInfo;
import ua.bookloom.persistence.sqlite.SqliteSummaryRepository;
import ua.bookloom.persistence.sqlite.SqliteTmRepository;

/**
 * Every storage port bound to its SQLite adapter, all sharing one {@link SqliteDatabase}; the window installs this
 * variant (ADR-0043), the command line installs {@link InMemoryPersistenceModule}.
 *
 * <p>The database is opened when the injector first needs it, not at construction, so a file that cannot be created
 * surfaces as a provisioning failure at the application's startup step ({@code AppLifecycle} phase 2) rather than as a
 * crash while the module list is assembled.
 */
public final class SqlitePersistenceModule extends AbstractModule {

    private final Path file;
    private final Clock clock;

    /**
     * Creates the module for the database at {@code file}.
     *
     * @param file the database file; its folder must exist
     * @param clock stamps the name of an old database set aside
     */
    public SqlitePersistenceModule(final Path file, final Clock clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    protected void configure() {
        bind(ProviderConfigs.class).to(SqliteProviderConfigs.class).in(Singleton.class);
        bind(CheckpointPort.class).to(SqliteCheckpoint.class).in(Singleton.class);
        bind(DeferralRepository.class).to(SqliteDeferralRepository.class).in(Singleton.class);
        bind(GlossaryRepository.class).to(SqliteGlossaryRepository.class).in(Singleton.class);
        bind(LexiconRepository.class).to(SqliteLexiconRepository.class).in(Singleton.class);
        bind(ModelSettingsRepository.class)
                .to(SqliteModelSettingsRepository.class)
                .in(Singleton.class);
        bind(ProjectRepository.class).to(SqliteProjectRepository.class).in(Singleton.class);
        bind(RunRepository.class).to(SqliteRunRepository.class).in(Singleton.class);
        bind(SegmentRepository.class).to(SqliteSegmentRepository.class).in(Singleton.class);
        bind(SettingsRepository.class).to(SqliteSettingsRepository.class).in(Singleton.class);
        bind(StorageInfo.class).to(SqliteStorageInfo.class).in(Singleton.class);
        bind(SummaryRepository.class).to(SqliteSummaryRepository.class).in(Singleton.class);
        bind(TmRepository.class).to(SqliteTmRepository.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    SqliteDatabase database() {
        final Result<SqliteDatabase> opened = SqliteDatabase.open(file, clock);
        if (opened.isErr()) {
            final AppError error = Objects.requireNonNull(opened.error());
            throw new IllegalStateException("The database could not be opened: " + error.message(), error.cause());
        }
        return Objects.requireNonNull(opened.data());
    }
}
