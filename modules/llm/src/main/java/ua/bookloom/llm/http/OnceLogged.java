package ua.bookloom.llm.http;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ProviderConfig;

/**
 * Writes one ERROR line per provider and model for a refusal that is a BookLoom fault, so a request built wrong is
 * loud once and not on every retry of a book.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OnceLogged {

    // The server blames a request BookLoom shaped; the person cannot fix it, so the developer must see it.
    private static final Set<FailureReason> BOOKLOOM_BUGS = EnumSet.of(FailureReason.OPTION_TYPE);
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    static void bugIfFirst(final FailureReason reason, final ProviderConfig config, final @Nullable String modelName) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(config, "config");
        if (BOOKLOOM_BUGS.contains(reason) && SEEN.add(reason.token() + "|" + modelName)) {
            log.error(
                    "Provider rejected a request BookLoom built wrong reason={} provider={} model={}",
                    reason.token(),
                    config.id(),
                    modelName);
        }
    }
}
