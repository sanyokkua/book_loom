package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;

/** What the call's circumstances change in a resolved profile. */
class ProfileAdjustmentsTest {

    private static final ReasoningControl SWITCHABLE =
            new ReasoningControl(ReasoningControl.Kind.BOOLEAN, List.of(), "false", null);

    // IF a model seen thinking kept the profile that sends no control and no room, THEN every call to it would starve
    // its answer and need the repeat; it must be treated as one that thinks on every call.
    @Test
    void of_modelSeenThinkingWithNoControl_isUnswitchable() {
        final ModelProfile adjusted = ProfileAdjustments.of(ModelProfile.defaults(), false, true);

        assertThat(adjusted.reasoning().kind()).isEqualTo(ReasoningControl.Kind.UNSWITCHABLE);
    }

    // IF a model with a working off switch were reclassified, THEN its off switch would be lost for a reply it can
    // still turn off.
    @Test
    void of_switchableModelSeenThinking_keepsItsControl() {
        final ModelProfile base = withReasoning(SWITCHABLE);

        assertThat(ProfileAdjustments.of(base, false, true).reasoning()).isEqualTo(SWITCHABLE);
    }

    @Test
    void of_hostedModel_hasAHostedWindowPolicy() {
        final ModelProfile adjusted = ProfileAdjustments.of(ModelProfile.defaults(), true, false);

        assertThat(adjusted.context().hosted()).isTrue();
        assertThat(adjusted.reasoning()).isEqualTo(ModelProfile.defaults().reasoning());
    }

    @Test
    void of_localModelNeverSeenThinking_isTheSameProfile() {
        final ModelProfile base = ModelProfile.defaults();

        assertThat(ProfileAdjustments.of(base, false, false)).isSameAs(base);
    }

    private static ModelProfile withReasoning(final ReasoningControl control) {
        final ModelProfile d = ModelProfile.defaults();
        return new ModelProfile(
                control,
                d.markupFilters(),
                d.systemRole(),
                d.quirks(),
                d.sampling(),
                d.thinking(),
                d.context(),
                d.templateOverhead(),
                d.batchSize(),
                d.tier());
    }
}
