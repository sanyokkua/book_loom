package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Project;

/**
 * The brief's Chapter summaries switch as the project store holds it now. The Book Brief may change it while a run is
 * paused, and a resume wakes the same job, whose run frame keeps the brief as it stood at Start; so the run reads the
 * switch here, and the change applies from the resume, as a new run would see it. A project that cannot be read keeps
 * the last value read, since a summary is no reason to end a run.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class SummariesSwitch implements BooleanSupplier {

    private final RunStores stores;
    private final String projectId;
    private boolean last;

    /**
     * Binds the switch to one run's project.
     *
     * @param stores the non-null stores the project is read from
     * @param projectId the non-null project id
     * @param atStart the switch as the run's brief held it at Start
     */
    public SummariesSwitch(final RunStores stores, final String projectId, final boolean atStart) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.last = atStart;
    }

    @Override
    public boolean getAsBoolean() {
        final Result<Optional<Project>> found = stores.projects().find(projectId);
        final Optional<Project> project = Optional.ofNullable(found.data()).flatMap(Function.identity());
        if (project.isEmpty()) {
            final AppError error = found.error();
            log.warn(
                    "Chapter summaries switch not read project={} reason={}; keeping on={}",
                    projectId,
                    error == null ? "no project" : error.code(),
                    last);
            return last;
        }
        final boolean on = project.get().brief().summariesOn();
        log.debug("Chapter summaries switch read project={} on={} before={}", projectId, on, last);
        if (on != last) {
            log.info("Chapter summaries switch changed project={} on={}", projectId, on);
        }
        last = on;
        return on;
    }
}
