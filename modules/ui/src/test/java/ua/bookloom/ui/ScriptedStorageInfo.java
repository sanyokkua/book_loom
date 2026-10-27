package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.StorageInfo;

/** A hand-written {@link StorageInfo}: it names a file and a size as told and records each backup and delete it is asked for. */
public final class ScriptedStorageInfo implements StorageInfo {

    private final @Nullable Path location;
    private final long size;
    private final List<Path> backups = new CopyOnWriteArrayList<>();
    private volatile @Nullable AppError backupFailure;
    private volatile @Nullable Path setAside;
    private volatile @Nullable AppError deleteFailure;
    private final AtomicInteger deletes = new AtomicInteger();

    /** Storage kept in {@code location}, {@code size} bytes large. */
    public ScriptedStorageInfo(final @Nullable Path location, final long size) {
        this.location = location;
        this.size = size;
    }

    /** Storage that lives in a made-up file of 1.5 MB. */
    public static ScriptedStorageInfo typical() {
        return new ScriptedStorageInfo(Path.of("/data/BookLoom/bookloom.db"), 1_572_864L);
    }

    /** Makes every later backup fail with {@code failure}. */
    public void failBackupWith(final AppError failure) {
        backupFailure = failure;
    }

    /** From now on reports that this launch moved the stored work to {@code aside}. */
    public void setAsideAs(final Path aside) {
        setAside = aside;
    }

    @Override
    public Optional<Path> setAsideAtOpen() {
        return Optional.ofNullable(setAside);
    }

    /** The destinations backups were asked for, in order. */
    public List<Path> backups() {
        return List.copyOf(backups);
    }

    @Override
    public Optional<Path> location() {
        return Optional.ofNullable(location);
    }

    @Override
    public long sizeBytes() {
        return size;
    }

    @Override
    public Result<Path> backupTo(final Path destination) {
        backups.add(destination);
        final AppError failure = backupFailure;
        return failure == null ? Result.ok(destination) : Result.err(failure);
    }

    /** Makes every later delete fail with {@code failure}. */
    public void failDeleteWith(final AppError failure) {
        deleteFailure = failure;
    }

    /** How many times the stored work was deleted. */
    public int deletes() {
        return deletes.get();
    }

    @Override
    public Result<Boolean> deleteAll() {
        deletes.incrementAndGet();
        final AppError failure = deleteFailure;
        return failure == null ? Result.ok(true) : Result.err(failure);
    }
}
