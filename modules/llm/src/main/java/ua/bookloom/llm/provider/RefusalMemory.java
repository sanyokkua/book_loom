package ua.bookloom.llm.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.RefusedSetting;

/**
 * Remembers which optional settings a model refused, per provider and model, on that model's own settings record so
 * the person can see them and send them again.
 */
@Slf4j
@Singleton
public final class RefusalMemory {

    private final ModelSettingsCache cache;
    private final Clock clock;

    @Inject
    public RefusalMemory(final ModelSettingsCache cache) {
        this(cache, Clock.system(ZoneId.systemDefault()));
    }

    RefusalMemory(final ModelSettingsCache cache, final Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The refused settings stored in a model's settings that cover every call to it.
     *
     * @param settings the model's settings; non-null
     * @return the capabilities; never null, empty when none; a stored name nobody knows is skipped
     */
    public EnumSet<RejectedCapability> remembered(final ModelSettings settings) {
        return remembered(settings, null);
    }

    /**
     * The refused settings that cover one call: those of the whole model, and those of the call's own scope.
     *
     * @param settings the model's settings; non-null
     * @param scope the call's scope (the name of the answer schema it asks for), or null when it asks for none
     * @return the capabilities; never null, empty when none; a stored name nobody knows is skipped
     */
    public EnumSet<RejectedCapability> remembered(final ModelSettings settings, final @Nullable String scope) {
        Objects.requireNonNull(settings, "settings");
        final EnumSet<RejectedCapability> found = EnumSet.noneOf(RejectedCapability.class);
        for (final RefusedSetting row : settings.refused()) {
            if (row.scope() != null && !row.scope().equals(scope)) {
                continue;
            }
            RejectedCapability.fromSettingName(row.name())
                    .ifPresentOrElse(
                            found::add, () -> log.debug("Unknown refused setting name={} ignored", row.name()));
        }
        log.debug(
                "Remembered refusals scope={} rows={} covering={}",
                scope,
                settings.refused().size(),
                found);
        return found;
    }

    /**
     * Strips the remembered refusals from a request.
     *
     * @param request the request; non-null
     * @param settings the model's settings; non-null
     * @return the request without them; the same request when it carries none of them
     */
    public ChatRequest withoutRemembered(final ChatRequest request, final ModelSettings settings) {
        Objects.requireNonNull(request, "request");
        final EnumSet<RejectedCapability> refused = remembered(settings);
        if (!refused.isEmpty()) {
            log.debug(
                    "Remembered refusals applied providerId={} modelId={} settings={}",
                    settings.providerId(),
                    settings.modelId(),
                    refused);
        }
        return SettingDrops.stripAll(request, refused);
    }

    /**
     * Stores a refusal on the model's settings, once.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model's id; non-null
     * @param setting the refused setting; non-null
     * @return true when the refusal is stored; false when the store could not save it
     */
    public boolean remember(final String providerId, final String modelId, final RejectedCapability setting) {
        return remember(providerId, modelId, setting, null);
    }

    /**
     * Stores a refusal of one scope on the model's settings, once.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model's id; non-null
     * @param setting the refused setting; non-null
     * @param scope the one call kind the refusal covers (the answer schema's name), or null for every call
     * @return true when the refusal is stored; false when the store could not save it
     */
    public boolean remember(
            final String providerId,
            final String modelId,
            final RejectedCapability setting,
            final @Nullable String scope) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(setting, "setting");
        final Result<ModelSettings> saved =
                cache.update(providerId, modelId, current -> withRefusal(current, setting, scope));
        if (saved.isErr()) {
            log.warn(
                    "Refused setting not remembered providerId={} modelId={} setting={} code={}",
                    providerId,
                    modelId,
                    setting,
                    errorCode(saved));
        }
        return saved.isOk();
    }

    // Runs inside the store's single step, so a speed stored at the same moment is not overwritten.
    private ModelSettings withRefusal(
            final ModelSettings current, final RejectedCapability setting, final @Nullable String scope) {
        if (remembered(current, scope).contains(setting)) {
            log.debug(
                    "Refusal already remembered providerId={} setting={} scope={}",
                    current.providerId(),
                    setting,
                    scope);
            return current;
        }
        final List<RefusedSetting> rows = new ArrayList<>(current.refused());
        rows.add(new RefusedSetting(setting.settingName(), LocalDate.now(clock), setting.insteadDid(), scope));
        return current.withRefused(rows);
    }

    private static @Nullable ErrorCode errorCode(final Result<ModelSettings> saved) {
        final AppError error = saved.error();
        return error == null ? null : error.code();
    }
}
