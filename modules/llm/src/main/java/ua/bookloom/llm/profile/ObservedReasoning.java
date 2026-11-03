package ua.bookloom.llm.profile;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.FailureReason;

/**
 * The models that showed, in this session, that they think on every call: a reply that spent its answer cap thinking
 * is the evidence, whatever the profile said before. A model never seen reasoning has a profile that sends no
 * reasoning control and gives no room for thinking; once seen, every later call to it gets the room, so the one repeat
 * a call is allowed is not what saves it each time. Kept for the session only: the model's facts are read again when
 * the person next acts.
 */
@Slf4j
public final class ObservedReasoning {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /**
     * Notes that a call to the model spent its cap thinking.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model id; non-null
     * @param reason the reason the call ended or was repeated with
     */
    public void note(final String providerId, final String modelId, final FailureReason reason) {
        if (seen.add(key(providerId, modelId))) {
            log.warn(
                    "The model thinks on every call and gets room for it from now on reason={} provider={} model={}",
                    reason.token(),
                    providerId,
                    modelId);
        }
    }

    /**
     * Whether the model was seen thinking.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model id; non-null
     * @return {@code true} if a call to it spent its cap thinking in this session, {@code false} otherwise
     */
    public boolean has(final String providerId, final String modelId) {
        return seen.contains(key(providerId, modelId));
    }

    private static String key(final String providerId, final String modelId) {
        return providerId + '\u0000' + modelId;
    }
}
