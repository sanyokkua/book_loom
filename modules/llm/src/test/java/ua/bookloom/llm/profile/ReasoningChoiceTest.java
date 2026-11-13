package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The order a call's reasoning is decided in (design D20): no control the model does not declare, then the person's
 * choice, then the model's own off value; a call that thinks gets room for it.
 */
class ReasoningChoiceTest {

    // kind, offValue, override, call kind -> the level sent (empty = no control), whether room is reserved, left out.
    @ParameterizedTest
    @CsvSource({
        "NONE, , ALWAYS, DRAFT, , false, true",
        "UNKNOWN, false, ALWAYS, DRAFT, , false, true",
        "UNSWITCHABLE, , RECOMMENDED, DRAFT, , true, true",
        "ALWAYS, low, RECOMMENDED, DRAFT, LOW, true, false",
        "ALWAYS, , ALWAYS, DRAFT, , true, true",
        "BOOLEAN, false, RECOMMENDED, PROOFREAD, OFF, false, false",
        "BOOLEAN, false, ALLOW_PROOFREAD_AND_NAMES, PROOFREAD, ON, true, false",
        "LEVELS, low, RECOMMENDED, DRAFT, LOW, true, false",
        "BOOLEAN, false, ALLOW_PROOFREAD_AND_NAMES, PRESCAN, ON, true, false",
        "BOOLEAN, false, ALLOW_PROOFREAD_AND_NAMES, BRIEF_SYNTHESIS, OFF, false, false",
        "BOOLEAN, false, ALWAYS, FILE_NAME, ON, true, false"
    })
    void decide_controlAndChoice_sendsTheExpectedLevel(
            final ReasoningControl.Kind kind,
            final @Nullable String offValue,
            final ThinkingOverride override,
            final CallKind callKind,
            final @Nullable ReasoningLevel sent,
            final boolean reserves,
            final boolean leftOut) {
        final ModelProfile profile = profile(new ReasoningControl(kind, List.of(), offValue, null), override);

        final ReasoningChoice.Decision decision = ReasoningChoice.decide(profile, request(callKind));

        assertThat(decision).isEqualTo(new ReasoningChoice.Decision(sent, reserves, leftOut));
    }

    private static ModelProfile profile(final ReasoningControl control, final ThinkingOverride override) {
        final ModelProfile d = ModelProfile.defaults();
        return new ModelProfile(
                control,
                Set.of(),
                d.systemRole(),
                d.quirks(),
                d.sampling(),
                override,
                d.context(),
                d.templateOverhead(),
                d.batchSize(),
                d.tier());
    }

    private static ChatRequest request(final CallKind kind) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "x")),
                null,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                500,
                null,
                kind);
    }
}
