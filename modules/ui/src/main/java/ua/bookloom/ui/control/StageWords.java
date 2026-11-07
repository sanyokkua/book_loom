package ua.bookloom.ui.control;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunStage;

/**
 * The words of a run's stage, shared by the title bar's stage chip and the Translating strip: "Prepare", "Translate",
 * "Proofread", at Max "Proofread · round 2 of 2: whole book", and at Fast "Proofread · marks for review, no model
 * calls". Pure; logs nothing, because both redraw on every change of the stage.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageWords {

    /**
     * A stage's name, as the strip lists every stage.
     *
     * @param messages the non-null catalogue
     * @param stage the non-null stage
     * @return "Prepare", "Translate" or "Proofread"
     */
    public static String name(final Messages messages, final JobStage stage) {
        Objects.requireNonNull(messages, "messages");
        return messages.code(MessageKey.RUN_STAGE_NAME, stage.name());
    }

    /**
     * The stage in work with what it does at the run's quality, as the chip and the strip's marked stage read it.
     *
     * @param messages the non-null catalogue
     * @param stage the non-null stage in work
     * @return the stage's name, or for Proofread its round at Max and its code-only reading at Fast
     */
    public static String inWork(final Messages messages, final RunStage stage) {
        Objects.requireNonNull(stage, "stage");
        final String name = name(messages, stage.stage());
        if (stage.stage() != JobStage.PROOFREAD) {
            return name;
        }
        if (stage.isCodeOnly()) {
            return messages.get(MessageKey.RUN_STAGE_PROOFREAD_FAST, name);
        }
        return stage.hasTwoRounds() && stage.round() > 0
                ? messages.get(MessageKey.RUN_STAGE_PROOFREAD_ROUND, name, String.valueOf(stage.round()))
                : name;
    }
}
