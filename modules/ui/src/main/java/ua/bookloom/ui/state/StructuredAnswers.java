package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether each model answered the last inference test of this session in the structured form. It is kept for the
 * session only: a model that was not tested since the app started has no answer, and the Models tab says so.
 *
 * <p>FX thread only.
 */
@Slf4j
@Singleton
public final class StructuredAnswers {

    private final Map<String, Boolean> answers = new HashMap<>();
    private final ReadOnlyLongWrapper revision = new ReadOnlyLongWrapper(0);

    /**
     * Notes the outcome of an inference test.
     *
     * @param providerId the stored provider
     * @param modelId the model that was tested
     * @param structured whether its reply was a well-formed structured answer
     */
    public void record(final String providerId, final String modelId, final boolean structured) {
        answers.put(key(providerId, modelId), structured);
        revision.set(revision.get() + 1);
        log.debug("structured answer noted for provider {}: {}", providerId, structured);
    }

    /**
     * What the last test of this session found.
     *
     * @param providerId the provider
     * @param modelId the model
     * @return whether the reply was structured, or empty when the model was not tested this session
     */
    public Optional<Boolean> lookup(final String providerId, final String modelId) {
        return Optional.ofNullable(answers.get(key(providerId, modelId)));
    }

    /** Counts every note, so a screen showing the answer can follow it. */
    public ReadOnlyLongProperty revision() {
        return revision.getReadOnlyProperty();
    }

    private static String key(final String providerId, final String modelId) {
        return providerId + '\u0000' + modelId;
    }
}
