package ua.bookloom.ui.screen;

import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * How one stage status is drawn, so that it is told apart by a glyph and by words, never by colour alone.
 *
 * @param glyph the mark shown before the text; distinct for every status
 * @param styleClass the one chip style class of the status
 * @param status the catalogue word for the status
 */
record ChipLook(String glyph, String styleClass, MessageKey status) {

    /**
     * Looks up the look of a status.
     *
     * @param status the outcome to draw
     * @return its look
     */
    static ChipLook of(final StageStatus status) {
        return switch (status) {
            case PASSED -> new ChipLook("✓", "chip-ok", MessageKey.SETTINGS_STATUS_PASSED);
            case SOFT_PASS -> new ChipLook("!", "chip-warn", MessageKey.SETTINGS_STATUS_SOFT_PASS);
            case FAILED -> new ChipLook("✕", "chip-err", MessageKey.SETTINGS_STATUS_FAILED);
            case SKIPPED -> new ChipLook("–", "chip-neutral", MessageKey.SETTINGS_STATUS_SKIPPED);
        };
    }

    /**
     * Names a stage for display.
     *
     * @param stage the stage
     * @return the catalogue key of its name
     */
    static MessageKey nameOf(final VerificationStage stage) {
        return switch (stage) {
            case CONNECTION -> MessageKey.SETTINGS_STAGE_CONNECTION;
            case MODELS -> MessageKey.SETTINGS_STAGE_MODELS;
            case INFERENCE -> MessageKey.SETTINGS_STAGE_INFERENCE;
        };
    }
}
