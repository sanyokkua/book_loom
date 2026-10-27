package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/** Holds the preferences for one process; nothing survives a restart (the SQLite twin does). */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemorySettingsRepository implements SettingsRepository {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    @Override
    public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
        Objects.requireNonNull(setting, "setting");
        try {
            final String raw = values.get(setting.key());
            final Optional<T> value = Optional.ofNullable(setting.parse(raw));
            log.debug("Setting read key={} stored={} present={}", setting.key(), raw != null, value.isPresent());
            return Result.ok(value);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public <T> Result<T> put(final AppSetting<T> setting, final T value) {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(value, "value");
        try {
            values.put(setting.key(), setting.format(value));
            log.debug("Setting stored key={}", setting.key());
            return Result.ok(value);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> remove(final AppSetting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        try {
            final boolean removed = values.remove(setting.key()) != null;
            log.debug("Setting removed key={} existed={}", setting.key(), removed);
            return Result.ok(removed);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Map<String, String>> all() {
        try {
            log.debug("Settings listed count={}", values.size());
            return Result.ok(Map.copyOf(values));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Settings storage failure", "The setting could not be stored.", null, cause);
        log.error("Unexpected settings repository failure code={}", error.code(), cause);
        return error;
    }
}
