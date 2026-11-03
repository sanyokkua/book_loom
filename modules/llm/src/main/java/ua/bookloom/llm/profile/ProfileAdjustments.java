package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ContextPolicy;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;

/**
 * What a call's circumstances change in the model's resolved profile: where the model runs, and whether it was seen
 * thinking. Both are known only at the call, not from the model's name or its server's facts.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProfileAdjustments {

    /**
     * The profile for this call.
     *
     * @param profile the resolved profile; non-null
     * @param hosted whether the model runs on the provider's hardware
     * @param seenThinking whether a call to the model spent its cap thinking
     * @return the profile with the hosted window policy and, for a model seen thinking whose profile sends no
     *     reasoning control, the control of a model that cannot be switched off; never null
     */
    public static ModelProfile of(final ModelProfile profile, final boolean hosted, final boolean seenThinking) {
        Objects.requireNonNull(profile, "profile");
        final ReasoningControl control = profile.reasoning();
        final boolean reclassify = seenThinking
                && (control.kind() == ReasoningControl.Kind.NONE || control.kind() == ReasoningControl.Kind.UNKNOWN);
        if (!hosted && !reclassify) {
            return profile;
        }
        log.debug("Profile adjusted hosted={} reclassifiedAsThinking={}", hosted, reclassify);
        final ContextPolicy context = profile.context();
        return new ModelProfile(
                reclassify ? new ReasoningControl(ReasoningControl.Kind.UNSWITCHABLE, List.of(), null, null) : control,
                profile.markupFilters(),
                profile.systemRole(),
                profile.quirks(),
                profile.sampling(),
                profile.thinking(),
                new ContextPolicy(context.hardCap(), context.outputMax(), context.detected(), context.loaded(), hosted),
                profile.templateOverhead(),
                profile.batchSize(),
                profile.tier());
    }
}
