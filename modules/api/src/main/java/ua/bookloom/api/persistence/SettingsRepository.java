package ua.bookloom.api.persistence;

import java.util.Map;
import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.settings.AppSetting;

/**
 * Keeps the person's preferences across restarts, one text per {@link AppSetting} key, so the window, the command
 * line and every adapter agree on what is stored.
 */
public interface SettingsRepository {

    /**
     * Reads one preference.
     *
     * @param setting the non-null key
     * @param <T> the value type
     * @return the stored value; the setting's default when nothing is stored or the stored text no longer parses;
     *     empty only when that default is absent (a key such as {@code ui.language} that was never set)
     */
    <T> Result<Optional<T>> get(AppSetting<T> setting);

    /**
     * Stores one preference, replacing the previous value (a value equal to the default is stored too).
     *
     * @param setting the non-null key
     * @param value the non-null value
     * @param <T> the value type
     * @return the stored value
     */
    <T> Result<T> put(AppSetting<T> setting, T value);

    /**
     * Lists what is stored.
     *
     * @return key to stored text for every preference that was ever set; never null, empty if none
     */
    Result<Map<String, String>> all();

    /**
     * Forgets one preference, so reading it gives the default again.
     *
     * @param setting the non-null key
     * @return {@code true} if a value was stored and is now gone, {@code false} if nothing was stored
     */
    Result<Boolean> remove(AppSetting<?> setting);
}
