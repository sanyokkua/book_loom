package ua.bookloom.pipeline;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.ProjectCopies;
import ua.bookloom.pipeline.project.ProjectServiceImpl;

/** Test shortcuts over {@link ProjectService}: what {@code list} and {@code open} say about one file's project. */
public final class ProjectServices {

    private ProjectServices() {}

    /** The decided-segment count {@code list} reports for the project whose source is {@code source}; 0 when none. */
    public static int decidedOf(final ProjectService service, final Path source) {
        final Result<List<ProjectSummary>> listed = service.list();
        final Path wanted = source.toAbsolutePath().normalize();
        return Objects.requireNonNull(listed.data(), () -> "list: " + listed.error()).stream()
                .filter(summary -> summary.project().source().equals(wanted))
                .mapToInt(ProjectSummary::decidedSegments)
                .findFirst()
                .orElse(0);
    }

    /** Opens again the project that an earlier import answered. */
    public static Result<ProjectOpening> reopen(final ProjectService service, final ImportedBook created) {
        return service.open(Objects.requireNonNull(created.projectId(), "project id"));
    }

    /** The book of an {@code open} answer that opened it; fails the test with the answer when it did not. */
    public static ImportedBook opened(final Result<ProjectOpening> answer) {
        final ProjectOpening opening = Objects.requireNonNull(answer.data(), () -> "open: " + answer.error());
        if (opening instanceof ProjectOpening.Opened opened) {
            return opened.book();
        }
        throw new AssertionError("the project did not open: " + opening);
    }

    /** A real service over the injector's repositories and the given documents, stores and open-book registry. */
    public static ProjectServiceImpl service(
            final Injector injector,
            final DocumentPort documents,
            final ProjectRepository projects,
            final SegmentRepository segments,
            final OpenProjects openProjects) {
        return new ProjectServiceImpl(
                injector.getInstance(BookInspector.class),
                documents,
                projects,
                segments,
                openProjects,
                Clock.systemUTC(),
                new UuidIdSource(),
                injector.getInstance(RunRepository.class),
                new ProjectCopies(
                        injector.getInstance(GlossaryRepository.class),
                        injector.getInstance(LexiconRepository.class),
                        segments,
                        new UuidIdSource()));
    }
}
