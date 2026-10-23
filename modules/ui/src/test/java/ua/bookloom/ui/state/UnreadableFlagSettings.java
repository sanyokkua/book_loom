package ua.bookloom.ui.state;

import java.util.Map;
import java.util.Optional;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/** A settings store whose first-launch flag cannot be read; everything else is the wrapped store. */
final class UnreadableFlagSettings implements SettingsRepository {

    private final SettingsRepository inner;

    UnreadableFlagSettings(final SettingsRepository inner) {
        this.inner = inner;
    }

    @Override
    public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
        return setting.key().equals(AppSetting.FIRST_LAUNCH_PROBED.key())
                ? Result.err(AppError.of(ErrorCode.internal, "Unreadable", "The store cannot be read."))
                : inner.get(setting);
    }

    @Override
    public <T> Result<T> put(final AppSetting<T> setting, final T value) {
        return inner.put(setting, value);
    }

    @Override
    public Result<Boolean> remove(final AppSetting<?> setting) {
        return inner.remove(setting);
    }

    @Override
    public Result<Map<String, String>> all() {
        return inner.all();
    }
}
