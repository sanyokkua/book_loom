package ua.bookloom.ui;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelReport;

/**
 * A hand-written {@link ModelCapabilities} that answers each model with the report the test scripted, records every
 * model it was asked about and how many asks came from the FX thread, and can hold the report of one model back.
 */
public final class ScriptedModelCapabilities implements ModelCapabilities {

    private static final long WAIT_SECONDS = 10;

    private final Map<String, ModelReport> reports = new ConcurrentHashMap<>();
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private final AtomicInteger fxThreadCalls = new AtomicInteger();
    private final Map<String, CountDownLatch> holds = new ConcurrentHashMap<>();

    /** A port that knows nothing about any model. */
    public static ScriptedModelCapabilities idle() {
        return new ScriptedModelCapabilities();
    }

    /** From now on reports {@code contextLength} and {@code loaded} (either may be null) for the model. */
    public void report(
            final String providerId,
            final String modelId,
            final @Nullable Integer contextLength,
            final @Nullable Integer loaded) {
        reports.put(providerId + "/" + modelId, new ModelReport(contextLength, loaded));
    }

    /** Makes every later report of this model wait for {@code release}, the way a slow provider would. */
    public void holdUntil(final String providerId, final String modelId, final CountDownLatch release) {
        holds.put(providerId + "/" + modelId, release);
    }

    /** Every {@code provider/model} asked about, oldest first. */
    public List<String> asked() {
        return List.copyOf(asked);
    }

    /** How many asks came from the FX Application Thread, which a provider call must never be. */
    public int fxThreadCalls() {
        return fxThreadCalls.get();
    }

    @Override
    public Result<ContextLength> contextLength(final String providerId, final String modelId) {
        final ModelReport report = reports.get(providerId + "/" + modelId);
        return Result.ok(report == null ? ContextLength.unknown() : new ContextLength(report.contextLength()));
    }

    @Override
    public Result<ModelReport> report(final String providerId, final String modelId) {
        if (Platform.isFxApplicationThread()) {
            fxThreadCalls.incrementAndGet();
        }
        asked.add(providerId + "/" + modelId);
        final ModelReport report = reports.getOrDefault(providerId + "/" + modelId, new ModelReport(null, null));
        awaitHold(holds.get(providerId + "/" + modelId));
        return Result.ok(report);
    }

    private static void awaitHold(final @Nullable CountDownLatch release) {
        if (release == null) {
            return;
        }
        try {
            if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the report");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", interrupted);
        }
    }
}
