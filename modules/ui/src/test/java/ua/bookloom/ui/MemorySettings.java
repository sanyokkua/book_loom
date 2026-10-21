package ua.bookloom.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/** A settings store in a map: the real round trip through each key's codec, without :persistence (not a :ui edge). */
public final class MemorySettings implements SettingsRepository {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    @Override
    public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
        return Result.ok(Optional.ofNullable(setting.parse(values.get(setting.key()))));
    }

    @Override
    public <T> Result<T> put(final AppSetting<T> setting, final T value) {
        values.put(setting.key(), setting.formatter().apply(value));
        return Result.ok(value);
    }

    @Override
    public Result<Map<String, String>> all() {
        return Result.ok(Map.copyOf(values));
    }
}
