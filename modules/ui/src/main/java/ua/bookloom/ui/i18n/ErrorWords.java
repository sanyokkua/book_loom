package ua.bookloom.ui.i18n;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;

/**
 * The words a failure is shown in.
 *
 * <p>Every {@link AppError} is built in English below the interface (the services know no display language), so in
 * any other display language its title and message are the catalogue's words for its {@link ua.bookloom.api.ErrorCode};
 * in English the failure's own words are kept, because they name the case more closely than the code can. The
 * details are never touched: they are the typed allowlist values, shown as they are.
 */
// Checkstyle cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorWords {

    /**
     * The failure's title in the display language.
     *
     * @param messages the catalogue of the display language
     * @param error the failure
     * @return its own title in English, else the catalogue's title for its code
     */
    public static String title(final Messages messages, final AppError error) {
        Objects.requireNonNull(error, "error");
        return keepsOwnWords(messages) ? error.title() : messages.code(MessageKey.ERROR_CODE_TITLE, codeOf(error));
    }

    /**
     * The failure's one-sentence message in the display language.
     *
     * @param messages the catalogue of the display language
     * @param error the failure
     * @return its own message in English, else the catalogue's advice for the {@link FailureReason} its details carry,
     *     else the catalogue's message for its code
     */
    public static String message(final Messages messages, final AppError error) {
        Objects.requireNonNull(error, "error");
        if (keepsOwnWords(messages)) {
            return error.message();
        }
        final Optional<FailureReason> reason = FailureReason.in(error.details());
        log.debug(
                "failure {} worded by {}",
                error.code(),
                reason.map(FailureReason::token).orElse("its code"));
        return reason.map(why -> messages.code(MessageKey.ERROR_REASON_MESSAGE, why.token()))
                .orElseGet(() -> messages.code(MessageKey.ERROR_CODE_MESSAGE, codeOf(error)));
    }

    private static String codeOf(final AppError error) {
        return error.code().name();
    }

    private static boolean keepsOwnWords(final Messages messages) {
        Objects.requireNonNull(messages, "messages");
        final boolean english =
                Locale.ENGLISH.getLanguage().equals(messages.locale().getLanguage());
        log.debug("failure words: {}", english ? "the failure's own (English)" : "the catalogue's for its code");
        return english;
    }
}
