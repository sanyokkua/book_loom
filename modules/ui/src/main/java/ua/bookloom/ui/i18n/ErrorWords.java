package ua.bookloom.ui.i18n;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.DetailCounts;
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

    // The selector of a reason's words that show the prompt and window counts, and of words that do not.
    private static final String COUNTED = "counted";
    private static final String NOT_COUNTED = "none";

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
     * @return its own message in English, else the catalogue's advice for the {@link FailureReason} its details carry
     *     when that reason has advice of its own, else the catalogue's message for its code
     */
    public static String message(final Messages messages, final AppError error) {
        Objects.requireNonNull(error, "error");
        if (keepsOwnWords(messages)) {
            return error.message();
        }
        final Optional<String> byReason =
                FailureReason.in(error.details()).flatMap(why -> reasonWords(messages, why, error.details()));
        log.debug("failure {} worded by {}", error.code(), byReason.isPresent() ? "its reason" : "its code");
        return byReason.orElseGet(() -> messages.code(MessageKey.ERROR_CODE_MESSAGE, codeOf(error)));
    }

    // A reason with no advice of its own (unclassified, or one the catalogue has no branch for yet) says less than the
    // code does, so the code's words are kept instead of the catalogue's catch-all. Every reason is given the prompt
    // and window counts its details hold, as {2} counted or none, {3} and {4}; only a branch that shows them reads
    // them.
    private static Optional<String> reasonWords(
            final Messages messages, final FailureReason why, final @Nullable String details) {
        if (why == FailureReason.UNCLASSIFIED) {
            return Optional.empty();
        }
        final OptionalInt prompt = DetailCounts.promptTokensIn(details);
        final OptionalInt window = DetailCounts.windowTokensIn(details);
        final boolean counted = prompt.isPresent() && window.isPresent();
        final String words = messages.code(
                MessageKey.ERROR_REASON_MESSAGE,
                why.token(),
                counted ? COUNTED : NOT_COUNTED,
                prompt.orElse(0),
                window.orElse(0));
        final String catchAll = messages.get(MessageKey.ERROR_REASON_MESSAGE, "other", why.token());
        final boolean ownAdvice = !words.equals(catchAll);
        log.debug("reason {} has its own advice: {} counted={}", why.token(), ownAdvice, counted);
        return ownAdvice ? Optional.of(words) : Optional.empty();
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
