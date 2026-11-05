package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The scene summary line under the live detail: the open project's newest chapter summary, read off the FX thread when
 * the project changes and again whenever the live calls carry another summary version.
 *
 * <p>It shows the text of the summary saved last, and nothing while the project has none. A read
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
     * @param summaries the stored chapter summaries
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
     * @param summaries the stored chapter summaries
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
     * @return the newest chapter summary's text, holding {@code null} while there is none; FX only
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
            final String found = textOf(summaries.list(projectId), projectId);
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

    private static @Nullable String textOf(final Result<List<ChapterSummary>> read, final String projectId) {
        if (read.isErr()) {
            log.warn("scene summary: the chapter summaries of project {} could not be read", projectId);
            return null;
        }
        final List<ChapterSummary> stored = Objects.requireNonNull(read.data());
        if (stored.isEmpty()) {
            log.debug("scene summary: project {} has no chapter summary yet", projectId);
            return null;
        }
        final ChapterSummary newest = stored.getLast();
        log.debug("scene summary: project {} shows chapter {} of {}", projectId, newest.chapterKey(), stored.size());
        log.trace("scene summary text: {}", newest.text());
        return newest.text();
    }
}
