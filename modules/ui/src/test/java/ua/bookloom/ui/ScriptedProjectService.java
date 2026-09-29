package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;

/**
 * A hand-written {@link ProjectService} that answers every import with one scripted project id and records the
 * source of each import and every brief it was asked to save.
 */
public final class ScriptedProjectService implements ProjectService {

    /** The project id every import answers with. */
    public static final String PROJECT_ID = "scripted-project";

    private final List<Path> imports = new CopyOnWriteArrayList<>();
    private final List<BookBrief> briefs = new CopyOnWriteArrayList<>();

    @Override
    public Result<ImportedBook> importBook(final Path source) {
        imports.add(Objects.requireNonNull(source, "source"));
        final BookInspection inspection = new BookInspection(
                InspectionVerdict.READABLE,
                BookFormat.TXT,
                null,
                "TXT",
                null,
                new LanguageEvidence(null, null, "en", LanguageEvidence.Verdict.ABSENT));
        return Result.ok(new ImportedBook(PROJECT_ID, inspection, null, BookBrief.defaults("en")));
    }

    @Override
    public Result<Project> updateBrief(final String projectId, final BookBrief brief) {
        Objects.requireNonNull(projectId, "projectId");
        briefs.add(Objects.requireNonNull(brief, "brief"));
        return Result.ok(new Project(projectId, imports.getLast(), BookFormat.TXT, "hash", brief));
    }

    @Override
    public Result<BookPlan> plan(final String projectId) {
        return notScripted();
    }

    @Override
    public Result<RoundTripReport> roundTrip(final String projectId) {
        return notScripted();
    }

    @Override
    public Result<Boolean> close(final String projectId) {
        return notScripted();
    }

    /** The source of every import, in order. */
    public List<Path> imports() {
        return List.copyOf(imports);
    }

    /** Every brief saved through {@link #updateBrief}, in order. */
    public List<BookBrief> briefs() {
        return List.copyOf(briefs);
    }

    private static <T> Result<T> notScripted() {
        return Result.err(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted project service has no answer."));
    }
}
