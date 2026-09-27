package ua.bookloom.llm.provider;

import ua.bookloom.api.ErrorCode;

/** Decides which model-discovery failures both dialect clients degrade to {@link ErrorCode#discoveryFailed}. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class DiscoveryErrors {

    private DiscoveryErrors() {}

    /**
     * Reports whether a discovery failure's own code should reach the caller unchanged.
     *
     * @param code the error code a discovery call failed with
     * @return {@code true} when the code is preserved as-is, {@code false} when it degrades to
     *     {@link ErrorCode#discoveryFailed}
     */
    public static boolean preserve(ErrorCode code) {
        return switch (code) {
            case auth, unreachable, timeout, cancelled -> true;
            case rateLimited,
                    modelNotFound,
                    modelUnavailable,
                    discoveryFailed,
                    upstream,
                    missingCredential,
                    contextWindow,
                    emptyCompletion,
                    validation,
                    internal,
                    busy -> false;
        };
    }
}
