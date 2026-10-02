package ua.bookloom.app.cli;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NamePolicy;

/**
 * Imports the command's book into a stored project and saves on its brief what the command names: the languages, and
 * the quality dial and name policy when given — the choices a person makes on the Book Brief screen.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
final class BookOpener {

    /**
     * The opened project.
     *
     * @param projectId the stored project's id
     * @param brief the brief as saved
     */
    record Opened(String projectId, BookBrief brief) {}

    private final ProjectService projects;

    /**
     * Imports the book and saves its brief.
     *
     * @param arguments the parsed command; never null
     * @return the project and its brief, or the error the import or the save answered
     */
    Result<Opened> open(TranslateArguments arguments) {
        Objects.requireNonNull(arguments, "arguments");
        final Result<ImportedBook> imported = projects.importBook(arguments.source());
        if (imported.isErr()) {
            return Result.err(Objects.requireNonNull(imported.error()));
        }
        final ImportedBook book = Objects.requireNonNull(imported.data());
        final @Nullable String projectId = book.projectId();
        final @Nullable BookBrief opened = book.brief();
        log.debug(
                "translate command import projectId={} verdict={}",
                projectId,
                book.inspection().verdict());
        if (projectId == null || opened == null) {
            log.warn(
                    "translate command book refused verdict={}",
                    book.inspection().verdict());
            return Result.err(refusal(book.inspection().verdict()));
        }
        return save(projectId, briefFor(opened, arguments));
    }

    private Result<Opened> save(String projectId, BookBrief brief) {
        final Result<?> saved = projects.updateBrief(projectId, brief);
        log.debug(
                "translate command brief saved={} source={} target={} dial={} names={}",
                saved.isOk(),
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.dial(),
                brief.names());
        return saved.isErr()
                ? Result.err(Objects.requireNonNull(saved.error()))
                : Result.ok(new Opened(projectId, brief));
    }

    private static BookBrief briefFor(BookBrief opened, TranslateArguments arguments) {
        final String source = arguments.sourceLanguage() == null ? opened.sourceLanguage() : arguments.sourceLanguage();
        final BookBrief languages = opened.withLanguages(source, arguments.targetLanguage());
        final RunOptions options = arguments.options();
        final NamePolicy names = Objects.requireNonNullElse(options.names(), languages.names());
        final QualityDial dial = Objects.requireNonNullElse(options.quality(), languages.dial());
        return new BookBrief(
                languages.sourceLanguage(),
                languages.targetLanguage(),
                languages.genre(),
                languages.register(),
                languages.voiceEra(),
                languages.audience(),
                names,
                languages.foreignPassages(),
                languages.footnotes(),
                languages.units(),
                languages.balance(),
                languages.alsoTranslate(),
                dial);
    }

    private static AppError refusal(InspectionVerdict verdict) {
        return switch (verdict) {
            case DRM_PROTECTED ->
                AppError.of(
                        ErrorCode.validation,
                        "This book is protected",
                        "This book is protected and cannot be translated. Its content is encrypted, so there is"
                                + " nothing to translate.");
            case UNSUPPORTED, READABLE ->
                AppError.of(
                        ErrorCode.validation,
                        "This file could not be opened",
                        "This file could not be read as a book — its structure is missing, malformed, or not a format"
                                + " this application supports.");
        };
    }
}
