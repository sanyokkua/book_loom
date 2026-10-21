package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/**
 * Keeps the preferences in the {@code settings(key, value, type)} table, so they survive a restart. The text of a
 * value is the one {@link AppSetting} formats; {@code type} only labels it for a person reading the file.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteSettingsRepository implements SettingsRepository {

    private static final String UPSERT = """
            INSERT INTO settings (key, value, type) VALUES (:key, :value, :type)
            ON CONFLICT (key) DO UPDATE SET value = excluded.value, type = excluded.type
            """;

    private final SqliteDatabase db;

    @Override
    public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
        Objects.requireNonNull(setting, "setting");
        return db.read("settings get", h -> {
            final Optional<String> raw = h.createQuery("SELECT value FROM settings WHERE key = :key")
                    .bind("key", setting.key())
                    .mapTo(String.class)
                    .findOne();
            final Optional<T> value = Optional.ofNullable(setting.parse(raw.orElse(null)));
            log.debug("Setting read key={} stored={} present={}", setting.key(), raw.isPresent(), value.isPresent());
            return value;
        });
    }

    @Override
    public <T> Result<T> put(final AppSetting<T> setting, final T value) {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(value, "value");
        return db.write("settings put", h -> {
            h.createUpdate(UPSERT)
                    .bind("key", setting.key())
                    .bind("value", setting.format(value))
                    .bind("type", typeOf(value))
                    .execute();
            log.debug("Setting stored key={}", setting.key());
            return value;
        });
    }

    @Override
    public Result<Map<String, String>> all() {
        return db.read("settings all", h -> {
            final Map<String, String> all = h
                    .createQuery("SELECT key, value FROM settings")
                    .map((rs, ctx) -> Map.entry(rs.getString(1), rs.getString(2)))
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            log.debug("Settings listed count={}", all.size());
            return all;
        });
    }

    private static String typeOf(final Object value) {
        return switch (value) {
            case Boolean ignored -> "boolean";
            case Enum<?> ignored -> "enum";
            default -> "string";
        };
    }
}
