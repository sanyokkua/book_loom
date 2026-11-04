package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.ui.IoExecutor;

/**
 * The readings behind the advice lines of the Models tab and of Start, by model and language pair. A reading is made
 * on the I/O executor, never on the FX thread, and the map changes on the FX thread only. A reading never asks the
 * server: it comes from the name and the facts the session already holds, so a screen may ask for it when it is built
 * (reopening the last project at launch included). The server is read only on the person's actions (selecting a model,
 * the Models tab, Test, Detect again), and each such read is announced through {@link #factsRead}, which reads the
 * model's held readings again.
 */
@Slf4j
@Singleton
public final class AdviceReader {

    /** Which model and pair a reading belongs to; a language that is not set is an empty text. */
    public record Key(String providerId, String modelId, String sourceTag, String targetTag) {

        public Key {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(sourceTag, "sourceTag");
            Objects.requireNonNull(targetTag, "targetTag");
        }

        static Key of(final ModelSelection selection, final @Nullable String source, final @Nullable String target) {
            return new Key(
                    selection.providerId(),
                    selection.modelId(),
                    source == null ? "" : source,
                    target == null ? "" : target);
        }
    }

    // The reading each key last asked for, so an unchanged model and pair is read once and a late, older reading never
    // replaces a newer one; FX thread only. A failed reading leaves its key, so the next ask reads again.
    private final Map<Key, Long> asked = new HashMap<>();
    private final ModelCapabilities capabilities;
    private final ExecutorService io;
    private final ObservableMap<Key, AdviceReading> readings = FXCollections.observableHashMap();
    private final ObservableMap<Key, AdviceReading> view = FXCollections.unmodifiableObservableMap(readings);
    private long epoch;

    /**
     * Creates a reader with nothing read.
     *
     * @param capabilities the port that reports a model and advises its languages
     * @param io the executor the readings run on, never the FX thread
     */
    @Inject
    public AdviceReader(final ModelCapabilities capabilities, @IoExecutor final ExecutorService io) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.io = Objects.requireNonNull(io, "io");
    }

    /**
     * The readings so far, observed by the lines that show them.
     *
     * @return a read-only map; FX thread only
     */
    public ObservableMap<Key, AdviceReading> readings() {
        return view;
    }

    /**
     * The reading of one model and pair.
     *
     * @param selection the stored provider and the model on it
     * @param source the source language tag, or null for none
     * @param target the target language tag, or null for none
     * @return the reading, or null while none has arrived; FX thread only
     */
    public @Nullable AdviceReading readingFor(
            final ModelSelection selection, final @Nullable String source, final @Nullable String target) {
        return readings.get(Key.of(selection, source, target));
    }

    /**
     * Asks for a reading from what is held, without asking the server. Returns at once; the reading arrives on the FX
     * thread. FX thread only.
     *
     * @param selection the stored provider and the model on it
     * @param source the source language tag, or null for none
     * @param target the target language tag, or null for none
     */
    public void request(final ModelSelection selection, final @Nullable String source, final @Nullable String target) {
        Objects.requireNonNull(selection, "selection");
        final Key key = Key.of(selection, source, target);
        if (asked.containsKey(key)) {
            return;
        }
        read(key);
    }

    /**
     * Reads the held readings of a model again, after a person's action read its server's facts. Safe on any thread;
     * the readings run on the I/O executor.
     *
     * @param selection the provider and model whose facts were read
     */
    public void factsRead(final ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        runOnFx(() -> {
            final List<Key> held = asked.keySet().stream()
                    .filter(key -> key.providerId().equals(selection.providerId())
                            && key.modelId().equals(selection.modelId()))
                    .toList();
            log.debug(
                    "facts of {}/{} read: {} reading(s) again",
                    selection.providerId(),
                    selection.modelId(),
                    held.size());
            held.forEach(this::read);
        });
    }

    private void read(final Key key) {
        final long mine = ++epoch;
        asked.put(key, mine);
        log.debug(
                "reading the advice of {}/{} for {}>{}",
                key.providerId(),
                key.modelId(),
                key.sourceTag(),
                key.targetTag());
        io.execute(() -> readHeld(key, mine));
    }

    private void readHeld(final Key key, final long mine) {
        final ModelSelection selection = new ModelSelection(key.providerId(), key.modelId());
        final @Nullable String source = key.sourceTag().isEmpty() ? null : key.sourceTag();
        final @Nullable String target = key.targetTag().isEmpty() ? null : key.targetTag();
        final Result<ModelReport> answer = capabilities.heldReport(selection);
        final ModelReport report = answer.data();
        if (report == null) {
            log.debug("no report for {}, no advice; asked again next time", selection.modelId());
            runOnFx(() -> asked.remove(key, mine));
            return;
        }
        final LanguageAdvice languages = capabilities.languageAdvice(selection, source, target);
        final AdviceReading reading =
                new AdviceReading(selection.modelId(), report, languages, capabilities.factsDate(), source, target);
        runOnFx(() -> {
            if (Objects.equals(asked.get(key), mine)) {
                readings.put(key, reading);
            } else {
                log.debug("advice reading #{} of {} dropped: a newer one is on the way", mine, key.modelId());
            }
        });
    }

    private static void runOnFx(final Runnable change) {
        if (Platform.isFxApplicationThread()) {
            change.run();
        } else {
            Platform.runLater(change);
        }
    }
}
