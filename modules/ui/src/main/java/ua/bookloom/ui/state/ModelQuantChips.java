package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.PastedModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.ui.IoExecutor;

/**
 * The quant chip of every model a picker has shown, by provider and model id.
 *
 * <p>A chip first comes from the name alone, which is pure and offline; a reading of the server's facts (one
 * {@code /api/show}, made only when the person selects, tests or detects) replaces it and is never replaced by a name
 * again. The map changes on the FX thread only; the readings run on the I/O executor.
 */
@Slf4j
@Singleton
public final class ModelQuantChips {

    /** Which model a chip belongs to. */
    public record Key(String providerId, String modelId) {

        public Key {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(modelId, "modelId");
        }
    }

    // Names already read, with or without a quant, so a name that has none is not read again; FX thread only.
    private final Set<Key> namesRead = new HashSet<>();
    private final ModelCapabilities capabilities;
    private final ExecutorService io;
    private final ObservableMap<Key, QuantChipModel> chips = FXCollections.observableHashMap();
    private final ObservableMap<Key, QuantChipModel> view = FXCollections.unmodifiableObservableMap(chips);

    /**
     * Creates an empty set of chips.
     *
     * @param capabilities the port that reads a name and a model's facts
     * @param io the executor the readings run on, never the FX thread
     */
    @Inject
    public ModelQuantChips(final ModelCapabilities capabilities, @IoExecutor final ExecutorService io) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.io = Objects.requireNonNull(io, "io");
    }

    /**
     * The chips known so far, observed by the cells that draw them.
     *
     * @return a read-only map; FX thread only
     */
    public ObservableMap<Key, QuantChipModel> chips() {
        return view;
    }

    /**
     * The chip of one model.
     *
     * @param providerId the provider's id
     * @param modelId the model id
     * @return the chip, or null while no quant is known; FX thread only
     */
    public @Nullable QuantChipModel chipFor(final String providerId, final String modelId) {
        return chips.get(new Key(providerId, modelId));
    }

    /**
     * Reads the quant of each listed name that has no chip yet. Returns at once; the chips arrive on the FX thread.
     *
     * @param providerId the provider the names are listed on
     * @param ids the listed model ids
     */
    public void fromNames(final String providerId, final List<String> ids) {
        Objects.requireNonNull(providerId, "providerId");
        final List<String> unknown = ids.stream()
                .filter(id -> namesRead.add(new Key(providerId, id)))
                .toList();
        if (unknown.isEmpty()) {
            return;
        }
        log.debug("reading the quant of {} listed name(s) of '{}'", unknown.size(), providerId);
        io.execute(() -> readNames(providerId, unknown));
    }

    /**
     * Reads a model's facts from its server. Returns at once; the chip arrives on the FX thread. FX thread only.
     *
     * @param selection a stored provider and the model on it
     */
    public void readFacts(final ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        log.debug("reading the quant facts of {}/{}", selection.providerId(), selection.modelId());
        io.execute(() -> readFactsNow(selection));
    }

    /**
     * Reads the facts of the model a form tests, when its provider is saved and a model is named. Returns at once.
     *
     * @param subject what the form describes; FX thread only
     */
    public void readFacts(final TestSubject subject) {
        final ModelSelection selection = selectionOf(subject);
        if (selection != null) {
            readFacts(selection);
        }
    }

    /**
     * As {@link #readFacts(TestSubject)}, on the calling thread, which must not be the FX thread.
     *
     * @param subject what a test ran against
     */
    public void readFactsNow(final TestSubject subject) {
        final ModelSelection selection = selectionOf(subject);
        if (selection != null) {
            readFactsNow(selection);
        }
    }

    private static @Nullable ModelSelection selectionOf(final TestSubject subject) {
        Objects.requireNonNull(subject, "subject");
        final String chosen = subject.model().strip();
        return subject.stored() && !chosen.isEmpty()
                ? new ModelSelection(subject.config().id(), chosen)
                : null;
    }

    /**
     * Reads a model's facts on the calling thread, which must not be the FX thread, and publishes the chip.
     *
     * @param selection a stored provider and the model on it
     */
    public void readFactsNow(final ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        try {
            final Result<ModelReport> asked = capabilities.report(selection);
            final ModelReport report = asked.data();
            if (report != null) {
                publishFacts(selection, report.quantAdvice());
            }
        } catch (RuntimeException failure) {
            log.warn("the quant facts of {} could not be read", selection.modelId(), failure);
        }
    }

    /**
     * Publishes a chip from a report a caller already holds.
     *
     * @param selection the provider and model the advice is about
     * @param advice the report's quant advice, or null when it carries none
     */
    public void publishFacts(final ModelSelection selection, final @Nullable QuantAdvice advice) {
        Objects.requireNonNull(selection, "selection");
        if (advice == null || advice.token().isBlank()) {
            log.debug("no quant in the facts of {}", selection.modelId());
            return;
        }
        final Key key = new Key(selection.providerId(), selection.modelId());
        final QuantChipModel chip = new QuantChipModel(advice.token(), advice.tier(), true);
        runOnFx(() -> chips.put(key, chip));
    }

    /**
     * Reads a pasted link, command or file name. The reading only parses the text (it opens, runs and sends nothing),
     * so it is made on the calling thread, which keeps Enter after a paste exact.
     *
     * @param text what was pasted
     * @param kind the dialect of the provider the model is added to
     * @return the reading, or empty for a plain model id, blank text or a failed reading
     */
    public Optional<PastedModel> readPasted(final String text, final ProviderKind kind) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        try {
            return capabilities.readPasted(text.strip(), kind);
        } catch (RuntimeException failure) {
            log.warn("a pasted model name could not be read", failure);
            return Optional.empty();
        }
    }

    private void readNames(final String providerId, final List<String> ids) {
        final Map<Key, QuantChipModel> found = new HashMap<>();
        try {
            for (final String id : ids) {
                final QuantAdvice advice = capabilities.quantAdviceByName(id);
                if (advice != null && !advice.token().isBlank()) {
                    found.put(new Key(providerId, id), new QuantChipModel(advice.token(), advice.tier(), false));
                }
            }
        } catch (RuntimeException failure) {
            log.warn("a model name could not be read for its quant", failure);
        }
        if (!found.isEmpty()) {
            runOnFx(() -> found.forEach((key, chip) -> chips.merge(key, chip, ModelQuantChips::keepFacts)));
        }
    }

    // A reading of the facts is better than the name's; two readings of the same kind: the newer wins.
    private static QuantChipModel keepFacts(final QuantChipModel held, final QuantChipModel arriving) {
        return held.fromFacts() && !arriving.fromFacts() ? held : arriving;
    }

    private static void runOnFx(final Runnable change) {
        if (Platform.isFxApplicationThread()) {
            change.run();
        } else {
            Platform.runLater(change);
        }
    }
}
