package ua.bookloom.ui;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import javafx.application.Platform;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** A hand-written {@link ModelSettingsRepository} that keeps what a test stored per provider and model in memory. */
public final class ScriptedModelSettings implements ModelSettingsRepository {

    private final Map<String, ModelSettings> stored = new ConcurrentHashMap<>();
    private final AtomicInteger fxThreadCalls = new AtomicInteger();
    private final List<ModelSettings> saves = new CopyOnWriteArrayList<>();
    private volatile boolean failListing;
    private volatile Runnable duringFind = () -> {};

    /** Runs {@code action} at the start of every later {@code find}, the way another writer would in between. */
    public void duringFind(final Runnable action) {
        duringFind = action;
    }

    /** Every settings row that was saved, oldest first. */
    public List<ModelSettings> saves() {
        return List.copyOf(saves);
    }

    /** How many calls came from the FX Application Thread, which storage must never see. */
    public int fxThreadCalls() {
        return fxThreadCalls.get();
    }

    /** Makes every later listing of what is stored fail, as a broken disk would. */
    public void failListing() {
        failListing = true;
    }

    private void watch() {
        if (Platform.isFxApplicationThread()) {
            fxThreadCalls.incrementAndGet();
        }
    }

    /** Stores a measured speed, in seconds per segment of a Fast draft, for one provider and model. */
    public void measured(final String providerId, final String modelId, final double secondsPerSegment) {
        measured(providerId, modelId, secondsPerSegment, MeasuredFrom.TEST);
    }

    /** Stores a measured speed with the place it was measured, without counting as a save. */
    public void measured(
            final String providerId, final String modelId, final double secondsPerSegment, final MeasuredFrom source) {
        final ModelSettings base = ModelSettings.defaults(providerId, modelId);
        final ModelSettings seeded = new ModelSettings(
                providerId,
                modelId,
                base.temperatureChoice(),
                null,
                null,
                null,
                base.outputField(),
                List.of(),
                secondsPerSegment,
                source);
        stored.put(providerId + "/" + modelId, seeded);
    }

    @Override
    public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
        watch();
        duringFind.run();
        return Result.ok(Optional.ofNullable(stored.get(providerId + "/" + modelId)));
    }

    @Override
    public Result<ModelSettings> save(final ModelSettings settings) {
        saves.add(settings);
        stored.put(settings.providerId() + "/" + settings.modelId(), settings);
        return Result.ok(settings);
    }

    @Override
    public Result<ModelSettings> update(
            final String providerId, final String modelId, final UnaryOperator<ModelSettings> change) {
        watch();
        duringFind.run();
        final ModelSettings kept = stored.compute(
                providerId + "/" + modelId,
                (key, row) -> change.apply(row == null ? ModelSettings.defaults(providerId, modelId) : row));
        saves.add(kept);
        return Result.ok(kept);
    }

    @Override
    public Result<Boolean> delete(final String providerId, final String modelId) {
        watch();
        return Result.ok(stored.remove(providerId + "/" + modelId) != null);
    }

    @Override
    public Result<List<ModelSettings>> all() {
        watch();
        if (failListing) {
            return Result.err(AppError.of(ErrorCode.internal, "Not read", "The store refused."));
        }
        return Result.ok(List.copyOf(stored.values()));
    }
}
