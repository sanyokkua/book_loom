package ua.bookloom.llm.verify;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.client.MessageShaper;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.provider.RejectedCapability;

/**
 * Lays Test's probes out the way the model's template reads them, as a run's calls are (design D20): the model's
 * profile shapes the messages, a system role the profile or a remembered refusal says the template lacks is folded into
 * the user message, and a refusal met during this Test is remembered on the model like a run's call does.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
final class ProbeShaping {

    private final ModelProfiles profiles;
    private final ModelSettingsCache settings;
    private final RefusalMemory refusals;

    /**
     * The shaping of one Test of one model, from the model's profile and stored refusals as they are now.
     *
     * @param config the provider as this Test sees it, with the engine it found; non-null
     * @param modelId the model tested; non-null
     * @return a shaper for this Test's probes; never null
     */
    Shaper forModel(final ProviderConfig config, final String modelId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        final ModelSettings stored = settings.current(config.id(), modelId);
        final ModelProfile profile = profiles.resolve(config.effectiveEngine(), modelId, ModelFacts.unknown(), stored)
                .profile();
        final boolean refused = refusals.remembered(stored).contains(RejectedCapability.SYSTEM_ROLE);
        log.debug(
                "Probe shaping provider={} model={} systemRole={} systemRoleRefused={}",
                config.id(),
                modelId,
                profile.systemRole(),
                refused);
        return new Shaper(config.id(), modelId, profile, refused);
    }

    /** The shaping of one Test's probes; confined to the thread that runs the Test. */
    final class Shaper {

        private final String providerId;
        private final String modelId;
        private final ModelProfile profile;
        private boolean folded;

        private Shaper(
                final String providerId, final String modelId, final ModelProfile profile, final boolean folded) {
            this.providerId = providerId;
            this.modelId = modelId;
            this.profile = profile;
            this.folded = folded;
        }

        /**
         * A probe as it goes out.
         *
         * @param request the probe as built; non-null
         * @return the probe shaped for the model's template
         */
        ChatRequest shaped(final ChatRequest request) {
            final ChatRequest shaped = MessageShaper.shaped(profile, request);
            return folded ? MessageShaper.folded(shaped) : shaped;
        }

        /** The template refused the system role: every later probe of this Test folds it, and the model remembers. */
        void systemRoleRefused() {
            folded = true;
            final boolean stored = refusals.remember(providerId, modelId, RejectedCapability.SYSTEM_ROLE);
            log.debug("Probe system role refused provider={} model={} remembered={}", providerId, modelId, stored);
        }
    }
}
