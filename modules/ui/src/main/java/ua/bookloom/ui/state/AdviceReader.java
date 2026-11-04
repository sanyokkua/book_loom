package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
 * on the I/O executor, never on the FX thread, and the map changes on the FX thread only. A model's facts are read
 * when a person looks at it (the Models tab, the Book brief or the ready card shows it), never in the background.
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

    // Keys asked for already, so an unchanged model and pair is read once; FX thread only.
    private final Set<Key> asked = new HashSet<>();
    private final ModelCapabilities capabilities;
    private final ExecutorService io;
    private final ObservableMap<Key, AdviceReading> readings = FXCollections.observableHashMap();
    private final ObservableMap<Key, AdviceReading> view = FXCollections.unmodifiableObservableMap(readings);

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
     * Asks for a reading. Returns at once; the reading arrives on the FX thread.
     *
     * @param selection the stored provider and the model on it
     * @param source the source language tag, or null for none
     * @param target the target language tag, or null for none
     */
    public void request(final ModelSelection selection, final @Nullable String source, final @Nullable String target) {
        Objects.requireNonNull(selection, "selection");
        final Key key = Key.of(selection, source, target);
        if (!asked.add(key)) {
            return;
        }
        log.debug("reading the advice of {}/{} for {}>{}", key.providerId(), key.modelId(), source, target);
        io.execute(() -> read(selection, key, source, target));
    }

    private void read(
            final ModelSelection selection,
            final Key key,
            final @Nullable String source,
            final @Nullable String target) {
        try {
            final Result<ModelReport> asked = capabilities.report(selection);
            final ModelReport report = asked.data();
            if (report == null) {
                log.debug("no report for {}, no advice", selection.modelId());
                return;
            }
            final LanguageAdvice languages = capabilities.languageAdvice(selection, source, target);
            final AdviceReading reading =
                    new AdviceReading(selection.modelId(), report, languages, capabilities.factsDate(), source, target);
            runOnFx(() -> readings.put(key, reading));
        } catch (RuntimeException failure) {
            log.warn("the advice of {} could not be read", selection.modelId(), failure);
        }
    }

    private static void runOnFx(final Runnable change) {
        if (Platform.isFxApplicationThread()) {
            change.run();
        } else {
            Platform.runLater(change);
        }
    }
}
