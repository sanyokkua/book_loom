package ua.bookloom.ui;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/**
 * A hand-written {@link ModelCatalog} for the first-launch look: it answers a draft by its base address (an address
 * nobody scripted is "unreachable", the way a closed port is), answers a saved id by that id, and records every call
 * with the connect and request timeouts the draft carried. {@link #holdDrafts()} keeps drafted calls in flight until
 * {@link #releaseDrafts()}.
 */
public final class RecordingModelCatalog implements ModelCatalog {

    private static final long WAIT_SECONDS = 10;

    /** One drafted call: the address asked, the dialect, the id and the two timeouts of the description. */
    public record Draft(
            String address, ProviderKind kind, String id, Duration connectTimeout, Duration requestTimeout) {}

    private final Map<String, Result<List<ModelInfo>>> byAddress = new ConcurrentHashMap<>();
    private final Map<String, Result<List<ModelInfo>>> byId = new ConcurrentHashMap<>();
    private final List<Draft> drafts = new CopyOnWriteArrayList<>();
    private final List<String> ids = new CopyOnWriteArrayList<>();
    private volatile @Nullable CountDownLatch draftHold;

    /** A catalogue where nothing answers, on any address or id. */
    public static RecordingModelCatalog nothingAnswering() {
        return new RecordingModelCatalog();
    }

    /** From now on the draft with this base address lists {@code models}. */
    public RecordingModelCatalog answering(final String address, final String... models) {
        byAddress.put(Objects.requireNonNull(address, "address"), listOf(models));
        return this;
    }

    /** From now on the draft with this base address fails with {@code error}. */
    public RecordingModelCatalog failing(final String address, final AppError error) {
        byAddress.put(Objects.requireNonNull(address, "address"), Result.err(error));
        return this;
    }

    /** From now on the saved provider {@code id} lists {@code models}. */
    public RecordingModelCatalog answeringId(final String id, final String... models) {
        byId.put(Objects.requireNonNull(id, "id"), listOf(models));
        return this;
    }

    /** From now on the saved provider {@code id} fails with {@code error}. */
    public RecordingModelCatalog failingId(final String id, final AppError error) {
        byId.put(Objects.requireNonNull(id, "id"), Result.err(error));
        return this;
    }

    /** Makes every drafted call that has not entered yet block until {@link #releaseDrafts()}. */
    public void holdDrafts() {
        draftHold = new CountDownLatch(1);
    }

    /** Lets every held drafted call answer. */
    public void releaseDrafts() {
        final CountDownLatch latch = draftHold;
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public Result<List<ModelInfo>> listModels(final String providerId) {
        ids.add(Objects.requireNonNull(providerId, "providerId"));
        final Result<List<ModelInfo>> scripted = byId.get(providerId);
        return scripted != null ? scripted : unreachable();
    }

    @Override
    public Result<List<ModelInfo>> listModels(final ProviderConfig draft) {
        Objects.requireNonNull(draft, "draft");
        drafts.add(new Draft(
                draft.baseUrl().toString(), draft.kind(), draft.id(), draft.connectTimeout(), draft.requestTimeout()));
        awaitHold();
        final Result<List<ModelInfo>> scripted = byAddress.get(draft.baseUrl().toString());
        return scripted != null ? scripted : unreachable();
    }

    private void awaitHold() {
        final CountDownLatch latch = draftHold;
        if (latch == null) {
            return;
        }
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the drafted calls");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", interrupted);
        }
    }

    /** Every drafted call, oldest first. */
    public List<Draft> drafts() {
        return List.copyOf(drafts);
    }

    /** The base address of every drafted call, oldest first. */
    public List<String> draftAddresses() {
        return drafts.stream().map(Draft::address).toList();
    }

    /** The id of every call by saved id, oldest first. */
    public List<String> ids() {
        return List.copyOf(ids);
    }

    private static Result<List<ModelInfo>> unreachable() {
        return Result.err(AppError.of(ErrorCode.unreachable, "Not reachable", "Nothing answers there."));
    }

    private static Result<List<ModelInfo>> listOf(final String... models) {
        return Result.ok(java.util.Arrays.stream(models).map(ModelInfo::new).toList());
    }
}
