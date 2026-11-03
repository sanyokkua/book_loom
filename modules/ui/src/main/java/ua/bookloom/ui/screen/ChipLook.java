package ua.bookloom.ui.screen;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * How one status chip is drawn — a stage status, or an uncertain brief field — so that it is told apart by a glyph and
 * by words, never by colour alone.
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
     * The look of a quant chip for a tier; the glyph is empty because the chip's own words name the tier.
     *
     * @param tier how the quant rates, or null while the model's size is not known
     * @return the look, whose style class carries the role colour
     */
    static ChipLook of(final @Nullable QuantTier tier) {
        if (tier == null) {
            return new ChipLook("", "chip-neutral", MessageKey.SETTINGS_QUANT_TIER_UNMEASURED);
        }
        return switch (tier) {
            case EXCELLENT -> new ChipLook("", "chip-ok", MessageKey.SETTINGS_QUANT_TIER_EXCELLENT);
            case GOOD -> new ChipLook("", "chip-ok", MessageKey.SETTINGS_QUANT_TIER_GOOD);
            case ACCEPTABLE -> new ChipLook("", "chip-neutral", MessageKey.SETTINGS_QUANT_TIER_ACCEPTABLE);
            case POOR -> new ChipLook("", "chip-warn", MessageKey.SETTINGS_QUANT_TIER_POOR);
            case UNUSABLE -> new ChipLook("", "chip-err", MessageKey.SETTINGS_QUANT_TIER_UNUSABLE);
            case REMOVED -> new ChipLook("", "chip-err", MessageKey.SETTINGS_QUANT_TIER_REMOVED);
            case UNMEASURED -> new ChipLook("", "chip-neutral", MessageKey.SETTINGS_QUANT_TIER_UNMEASURED);
        };
    }

    /**
     * The look of a suggested Book Brief field the samples of the book did not agree on.
     *
     * @return the look of the uncertain chip
     */
    static ChipLook uncertain() {
        return new ChipLook("?", "chip-warn", MessageKey.BRIEF_EVIDENCE_UNCERTAIN);
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
            case ADVANCED -> MessageKey.SETTINGS_STAGE_ADVANCED;
        };
    }
}
