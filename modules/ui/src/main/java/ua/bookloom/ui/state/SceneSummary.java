package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The scene summary line under the live detail: the open project's newest rolling summary, read off the FX thread when
 * the project changes and again whenever the run says it refreshed the summary (the version the live calls carry).
 *
 * <p>It shows the summary's target-language text, else its source text, and nothing while the project has none. A read
 * that finishes after another project was opened is dropped. FX thread only, except the read itself.
 */
@Slf4j
@Singleton
public final class SceneSummary {

    private final SummaryRepository summaries;
    private final Executor background;
    private final Executor fx;
    private final CurrentProject current;
    private final ReadOnlyObjectWrapper<@Nullable String> text = new ReadOnlyObjectWrapper<>();
    private int shownVersion;

    /**
     * Follows the open project and the run's summary refreshes, reading on the background executor.
     *
     * @param summaries the stored rolling summaries
     * @param background where the summary is read
     * @param current the open project
     * @param mirror the run's state, whose live calls carry the summary's version
     */
    @Inject
    public SceneSummary(
            final SummaryRepository summaries,
            final @BackgroundExecutor ExecutorService background,
            final CurrentProject current,
            final StateMirror mirror) {
        this(summaries, background, Platform::runLater, current, mirror);
    }

    /**
     * Follows the open project and the run's summary refreshes with the given executors, as a test needs.
     *
     * @param summaries the stored rolling summaries
     * @param background where the summary is read
     * @param fx where the read text is shown
     * @param current the open project
     * @param mirror the run's state, whose live calls carry the summary's version
     */
    public SceneSummary(
            final SummaryRepository summaries,
            final Executor background,
            final Executor fx,
            final CurrentProject current,
            final StateMirror mirror) {
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.current = Objects.requireNonNull(current, "current");
        Objects.requireNonNull(mirror, "mirror");
        current.book().addListener((observed, was, now) -> read());
        mirror.live().calls().addListener((observed, was, now) -> {
            if (now.summaryVersion() != shownVersion) {
                shownVersion = now.summaryVersion();
                read();
            }
        });
        read();
    }

    /**
     * The text of the line.
     *
     * @return the summary's target text, else its source text, holding {@code null} while there is none; FX only
     */
    public ReadOnlyObjectProperty<@Nullable String> text() {
        return text.getReadOnlyProperty();
    }

    private void read() {
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("scene summary: no project open, nothing to read");
            text.set(null);
            return;
        }
        final String projectId = book.projectId();
        log.debug("scene summary: reading project {} at version {}", projectId, shownVersion);
        background.execute(() -> {
            final String found = textOf(summaries.latest(projectId), projectId);
            fx.execute(() -> show(projectId, found));
        });
    }

    private void show(final String projectId, final @Nullable String found) {
        final OpenedBook book = current.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("scene summary: the read of project {} came after another project opened; dropped", projectId);
            return;
        }
        text.set(found);
    }

    private static @Nullable String textOf(final Result<Optional<RollingSummary>> read, final String projectId) {
        final Optional<RollingSummary> stored = read.data();
        if (stored == null) {
            log.warn("scene summary: the summary of project {} could not be read", projectId);
            return null;
        }
        if (stored.isEmpty()) {
            log.debug("scene summary: project {} has no summary yet", projectId);
            return null;
        }
        final RollingSummary summary = stored.get();
        final String shown = summary.target().isBlank() ? summary.source() : summary.target();
        log.debug("scene summary: project {} has version {}", projectId, summary.version());
        log.trace("scene summary text: {}", shown);
        return shown.isBlank() ? null : shown;
    }
}
