package ua.bookloom.ui.dialog;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.MessageKey;

/** The words the test card keeps for each stage: the title it shows and the hover help of its tab. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestCardWords {

    static MessageKey tabTip(final VerificationStage which) {
        return switch (which) {
            case CONNECTION -> MessageKey.SETTINGS_STAGE_CONNECTION_TIP;
            case MODELS -> MessageKey.SETTINGS_STAGE_MODELS_TIP;
            case INFERENCE -> MessageKey.SETTINGS_STAGE_INFERENCE_TIP;
            case ADVANCED -> MessageKey.SETTINGS_STAGE_ADVANCED_TIP;
        };
    }

    static MessageKey titleOf(final VerificationStage which) {
        return switch (which) {
            case CONNECTION -> MessageKey.SETTINGS_TEST_CONNECTION;
            case MODELS -> MessageKey.SETTINGS_TEST_MODELS;
            case INFERENCE -> MessageKey.SETTINGS_TEST_INFERENCE;
            case ADVANCED -> MessageKey.SETTINGS_TEST_ADVANCED_TITLE;
        };
    }
}
