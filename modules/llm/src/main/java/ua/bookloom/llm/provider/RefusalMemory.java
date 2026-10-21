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
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
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
    // Only the keys serialise writers: a read-modify-write of one model's record runs inside compute() on its key.
    private final ConcurrentHashMap<String, Boolean> writing = new ConcurrentHashMap<>();

    @Inject
    public RefusalMemory(final ModelSettingsCache cache) {
        this(cache, Clock.system(ZoneId.systemDefault()));
    }

    RefusalMemory(final ModelSettingsCache cache, final Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The refused settings stored in a model's settings.
     *
     * @param settings the model's settings; non-null
     * @return the capabilities; never null, empty when none; a stored name nobody knows is skipped
     */
    public EnumSet<RejectedCapability> remembered(final ModelSettings settings) {
        Objects.requireNonNull(settings, "settings");
        final EnumSet<RejectedCapability> found = EnumSet.noneOf(RejectedCapability.class);
        for (final RefusedSetting row : settings.refused()) {
            RejectedCapability.fromSettingName(row.name())
                    .ifPresentOrElse(
                            found::add, () -> log.debug("Unknown refused setting name={} ignored", row.name()));
        }
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
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(setting, "setting");
        final boolean[] stored = {false};
        writing.compute(providerId + '\u0000' + modelId, (key, ignored) -> {
            stored[0] = store(providerId, modelId, setting);
            return Boolean.TRUE;
        });
        return stored[0];
    }

    private boolean store(final String providerId, final String modelId, final RejectedCapability setting) {
        final ModelSettings current = cache.current(providerId, modelId);
        if (remembered(current).contains(setting)) {
            log.debug("Refusal already remembered providerId={} modelId={} setting={}", providerId, modelId, setting);
            return true;
        }
        final List<RefusedSetting> rows = new ArrayList<>(current.refused());
        rows.add(new RefusedSetting(setting.settingName(), LocalDate.now(clock), setting.insteadDid()));
        final Result<ModelSettings> saved = cache.save(new ModelSettings(
                current.providerId(),
                current.modelId(),
                current.temperatureChoice(),
                current.temperature(),
                current.contextWindow(),
                current.maxOutput(),
                current.outputField(),
                rows,
                current.measuredSecondsPerSegment()));
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

    private static @org.jspecify.annotations.Nullable ErrorCode errorCode(final Result<ModelSettings> saved) {
        final AppError error = saved.error();
        return error == null ? null : error.code();
    }
}
