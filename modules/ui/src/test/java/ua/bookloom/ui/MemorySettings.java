package ua.bookloom.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/** A settings store in a map: the real round trip through each key's codec, without :persistence (not a :ui edge). */
public final class MemorySettings implements SettingsRepository {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final AtomicInteger fxThreadCalls = new AtomicInteger();
    private volatile boolean failingGets;
    private volatile boolean failingPuts;

    /** From now on every read answers a failure, like a store that cannot be opened. */
    public MemorySettings failGets() {
        failingGets = true;
        return this;
    }

    /** From now on every write answers a failure, like a store that cannot be written. */
    public MemorySettings failPuts() {
        failingPuts = true;
        return this;
    }

    private static AppError failure() {
        return AppError.of(ErrorCode.internal, "Storage failed", "The setting could not be reached.");
    }

    /** What is stored under a key now, read without counting as a window call. */
    public <T> Optional<T> stored(final AppSetting<T> setting) {
        return Optional.ofNullable(setting.parse(values.get(setting.key())));
    }

    /** How many calls came from the FX Application Thread, which storage must never see. */
    public int fxThreadCalls() {
        return fxThreadCalls.get();
    }

    private void watch() {
        if (Platform.isFxApplicationThread()) {
            fxThreadCalls.incrementAndGet();
        }
    }

    @Override
    public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
        watch();
        if (failingGets) {
            return Result.err(failure());
        }
        return Result.ok(Optional.ofNullable(setting.parse(values.get(setting.key()))));
    }

    @Override
    public <T> Result<T> put(final AppSetting<T> setting, final T value) {
        watch();
        if (failingPuts) {
            return Result.err(failure());
        }
        values.put(setting.key(), setting.formatter().apply(value));
        return Result.ok(value);
    }

    @Override
    public Result<Boolean> remove(final AppSetting<?> setting) {
        watch();
        return Result.ok(values.remove(setting.key()) != null);
    }

    @Override
    public Result<Map<String, String>> all() {
        return Result.ok(Map.copyOf(values));
    }
}
