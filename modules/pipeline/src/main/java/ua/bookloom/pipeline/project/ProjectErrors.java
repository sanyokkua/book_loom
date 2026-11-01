package ua.bookloom.pipeline.project;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/** The failures {@link ProjectServiceImpl} answers with, each logged once where it is built. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProjectErrors {

    static AppError unknownProject(final String projectId) {
        log.warn("unknown project={}", projectId);
        return AppError.of(
                ErrorCode.validation,
                "This project is not open",
                "No open project has this id; import the book again.");
    }

    static AppError unknownUnit(final String unitId) {
        log.warn("unknown unit={}", unitId);
        return AppError.of(
                ErrorCode.validation, "This part of the book is not known", "The book has no part with this id.");
    }

    static AppError internalError(final String action, final Throwable cause) {
        log.error("Failed to {}", action, cause);
        return AppError.of(
                ErrorCode.internal, "The project service failed", "The book could not be processed.", null, cause);
    }
}
