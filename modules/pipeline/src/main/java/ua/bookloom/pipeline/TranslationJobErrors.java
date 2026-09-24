package ua.bookloom.pipeline;

import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/** Supplies the stable validation errors used by the translation-job lifecycle. */
final class TranslationJobErrors {

    private TranslationJobErrors() {}

    static AppError destinationExists() {
        return AppError.of(
                ErrorCode.validation,
                "This destination already exists",
                "Choose a new destination or allow the existing file to be replaced.");
    }

    static AppError alreadyRun() {
        return AppError.of(
                ErrorCode.validation, "This job has already run", "Create a new translation job to run again.");
    }
}
