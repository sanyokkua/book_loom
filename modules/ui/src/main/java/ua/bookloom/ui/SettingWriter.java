package ua.bookloom.ui;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/**
 * One owner's way to its stored choices: reads that fall back to the default, and writes queued in the order they
 * were made on the background executor, with a flag that says whether the last one failed. The store refuses the FX
 * thread, so nothing here touches it there.
 *
 * <p>Not a singleton on purpose: each owner gets its own queue and its own failed-save flag, so one tab's failed write
 * is never shown on another.
 */
@Slf4j
public final class SettingWriter {

    private final SettingsRepository settings;
    private final Executor background;
    private final Executor fx;
    private final ReadOnlyBooleanWrapper saveFailed = new ReadOnlyBooleanWrapper(false);
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private volatile boolean lastWriteFailed;

    /**
     * Creates the writer over the real store, pool and FX thread.
     *
     * @param settings where the choices are kept
     * @param background where the store is read and written, never the FX thread
     */
    @Inject
    public SettingWriter(final SettingsRepository settings, final @BackgroundExecutor ExecutorService background) {
        this(settings, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied, so a test needs no toolkit.
     *
     * @param settings where the choices are kept
     * @param background where the store is read and written
     * @param fx runs a task on the FX Application Thread
     */
    public SettingWriter(final SettingsRepository settings, final Executor background, final Executor fx) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    /**
     * Reads a stored value off the FX thread; an unreadable store counts as nothing stored.
     *
     * @param setting the key
     * @return the stored value, or empty when none is stored or the store could not be read
     */
    public <T> Optional<T> stored(final AppSetting<T> setting) {
        final Optional<T> value = settings.get(setting).data();
        if (value == null) {
            log.warn("{} could not be read; its default applies", setting.key());
            return Optional.empty();
        }
        return value;
    }

    /**
     * Reads a stored value off the FX thread, or the setting's default.
     *
     * @param setting a key that has a default
     * @return the stored value, else the default
     */
    public <T> T read(final AppSetting<T> setting) {
        return stored(setting).orElseGet(() -> Objects.requireNonNull(setting.defaultValue(), "default"));
    }

    /**
     * Whether a value has ever been written under the key, read off the FX thread.
     *
     * @param setting the key
     * @return {@code true} if written, {@code false} if not or the keys could not be listed
     */
    public boolean isWritten(final AppSetting<?> setting) {
        final Map<String, String> all = settings.all().data();
        if (all == null) {
            log.warn("the stored keys could not be listed; {} counts as not written", setting.key());
            return false;
        }
        return all.containsKey(setting.key());
    }

    /**
     * Reads on the background executor and hands the answer to the FX thread.
     *
     * @param read what to read; runs off the FX thread
     * @param publish receives the answer on the FX thread
     */
    public <T> void load(final Supplier<T> read, final Consumer<T> publish) {
        Objects.requireNonNull(read, "read");
        Objects.requireNonNull(publish, "publish");
        background.execute(() -> {
            final T answer = read.get();
            fx.execute(() -> publish.accept(answer));
        });
    }

    /**
     * Stores a value once every earlier write has finished.
     *
     * @param setting the key
     * @param value the value
     */
    public <T> void put(final AppSetting<T> setting, final T value) {
        put(setting, value, () -> {});
    }

    /**
     * Stores a value once every earlier write has finished, then runs {@code afterStored} off the FX thread if it
     * was stored.
     *
     * @param setting the key
     * @param value the value
     * @param afterStored runs only after a successful write
     */
    public <T> void put(final AppSetting<T> setting, final T value, final Runnable afterStored) {
        Objects.requireNonNull(afterStored, "afterStored");
        write(setting.key(), () -> {
            final boolean stored = settings.put(setting, value).isOk();
            if (stored) {
                afterStored.run();
            }
            return stored;
        });
    }

    /**
     * Forgets a stored value once every earlier write has finished, so its default applies again.
     *
     * @param setting the key
     */
    public void remove(final AppSetting<?> setting) {
        write(setting.key(), () -> settings.remove(setting).isOk());
    }

    /**
     * Queues any write that answers whether it worked, and shows the answer in {@link #saveFailedProperty()}.
     *
     * @param what names the choice in the log
     * @param write runs off the FX thread; {@code true} when the choice was kept
     */
    public void write(final String what, final BooleanSupplier write) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(write, "write");
        log.debug("queueing the write of {}", what);
        afterWrites(() -> attempt(what, write), failed -> saveFailed.set(failed));
    }

    /**
     * Runs work on the background executor after every queued write, and hands its answer to the FX thread.
     *
     * @param work runs off the FX thread
     * @param publish receives the answer on the FX thread
     */
    public <T> void afterWrites(final Supplier<T> work, final Consumer<T> publish) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(publish, "publish");
        // Each task waits for the one before it, whether that one worked or not, so a quick second choice is never
        // overwritten by the first.
        lastWrite = lastWrite
                .handle((done, failure) -> (Void) null)
                .thenRunAsync(
                        () -> {
                            final T answer = work.get();
                            fx.execute(() -> publish.accept(answer));
                        },
                        background);
    }

    private boolean attempt(final String what, final BooleanSupplier write) {
        boolean failed;
        try {
            failed = !write.getAsBoolean();
        } catch (RuntimeException e) {
            log.error("storing {} failed", what, e);
            failed = true;
        }
        if (failed) {
            log.warn("{} could not be stored; it holds until the app closes", what);
        }
        lastWriteFailed = failed;
        return failed;
    }

    /** {@return whether the last write failed; read off the FX thread by work queued after it} */
    public boolean lastWriteFailed() {
        return lastWriteFailed;
    }

    /** {@return whether the last choice could not be stored; it still holds until the app closes} */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed.getReadOnlyProperty();
    }
}
