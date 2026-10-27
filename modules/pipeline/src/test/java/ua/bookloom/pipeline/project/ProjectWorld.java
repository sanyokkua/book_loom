package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CountingIdSource;
import ua.bookloom.pipeline.lexicon.LexiconServiceImpl;

/** The real project service over the real documents and the in-memory repositories, with shortcuts the tests share. */
final class ProjectWorld {

    final ProjectService service;
    final LexiconServiceImpl lexiconService;
    final ProjectRepository projects;
    final SegmentRepository segments;
    final GlossaryRepository glossary;
    final LexiconRepository lexicon;
    final RunRepository runs;
    final TmRepository tm;

    ProjectWorld() {
        final Injector injector =
                Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule(), binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(new CountingIdSource());
                });
        service = injector.getInstance(ProjectServiceImpl.class);
        lexiconService = injector.getInstance(LexiconServiceImpl.class);
        projects = injector.getInstance(ProjectRepository.class);
        segments = injector.getInstance(SegmentRepository.class);
        glossary = injector.getInstance(GlossaryRepository.class);
        lexicon = injector.getInstance(LexiconRepository.class);
        runs = injector.getInstance(RunRepository.class);
        tm = injector.getInstance(TmRepository.class);
    }

    /** Imports the file and answers the new project's id. */
    String create(final Path book) {
        final Result<ImportedBook> result = service.create(book);
        assertThat(result.isOk()).as("create result %s", result.error()).isTrue();
        return Objects.requireNonNull(Objects.requireNonNull(result.data()).projectId());
    }

    /** Sets the book's language pair and answers the stored project. */
    Project translate(final String id, final String source, final String target) {
        return saved(service.updateBrief(id, BookBrief.defaults(source).withLanguages(source, target)));
    }

    Project stored(final String id) {
        return Objects.requireNonNull(projects.find(id).data()).orElseThrow();
    }

    RunRecord latestRun(final String id) {
        return Objects.requireNonNull(runs.latest(id).data()).orElseThrow();
    }

    List<GlossaryEntry> glossaryOf(final String id) {
        return Objects.requireNonNull(glossary.all(id).data());
    }

    List<SegmentRecord> recordsOf(final String id) {
        return Objects.requireNonNull(segments.all(id).data());
    }

    List<Project> storedProjects() {
        return Objects.requireNonNull(projects.all().data());
    }

    static ProjectChange changeOf(final Result<ProjectChange> result) {
        assertThat(result.isOk()).as("result %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    static Project saved(final Result<ProjectChange> result) {
        final ProjectChange change = changeOf(result);
        assertThat(change).isInstanceOf(ProjectChange.Saved.class);
        return ((ProjectChange.Saved) change).project();
    }
}
