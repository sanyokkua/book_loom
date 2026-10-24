package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.ViewNames;

/** Turns the stored projects into the rows the Projects screen shows, grouped by book. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProjectRows {

    private static final int PERCENT = 100;

    /**
     * Groups the projects by the book they translate and decides each row's status, progress and step.
     *
     * @param summaries the stored projects, newest first; a group appears where its first project does
     * @param openId the id of the project open in the window, or {@code null} when none is
     * @param providerNames the display name of each provider id; an id with no name is shown as it is
     * @return the groups in list order; empty when nothing is stored
     */
    public static List<BookGroup> of(
            final List<ProjectSummary> summaries,
            final @Nullable String openId,
            final Map<String, String> providerNames) {
        Objects.requireNonNull(summaries, "summaries");
        Objects.requireNonNull(providerNames, "providerNames");
        final Map<String, List<ProjectSummary>> byBook = new LinkedHashMap<>();
        for (final ProjectSummary summary : summaries) {
            byBook.computeIfAbsent(summary.project().contentHash(), hash -> new ArrayList<>())
                    .add(summary);
        }
        final List<BookGroup> groups = byBook.values().stream()
                .map(book -> group(book, openId, providerNames))
                .toList();
        log.debug("{} project(s) grouped into {} book(s)", summaries.size(), groups.size());
        return groups;
    }

    /**
     * The row of one stored project, as the Projects screen would show it.
     *
     * @param summary the stored project with its counts
     * @param openId the id of the project open in the window, or {@code null} when none is
     * @return the row; its provider name is the provider's id
     */
    public static ProjectRow rowOf(final ProjectSummary summary, final @Nullable String openId) {
        return row(Objects.requireNonNull(summary, "summary"), openId, Map.of());
    }

    private static BookGroup group(
            final List<ProjectSummary> book, final @Nullable String openId, final Map<String, String> providerNames) {
        final ProjectSummary first = book.getFirst();
        final Path name = first.project().source().getFileName();
        return new BookGroup(
                first.project().contentHash(),
                first.project().title(),
                name == null ? first.project().source().toString() : name.toString(),
                first.project().format(),
                first.totalSegments(),
                book.stream()
                        .map(summary -> row(summary, openId, providerNames))
                        .toList());
    }

    private static ProjectRow row(
            final ProjectSummary summary, final @Nullable String openId, final Map<String, String> providerNames) {
        final int percent = summary.totalSegments() == 0
                ? 0
                : (int) ((long) summary.decidedSegments() * PERCENT / summary.totalSegments());
        return new ProjectRow(
                summary,
                percent,
                statusOf(summary),
                summary.project().id().equals(openId),
                stepOf(summary),
                providerNameOf(summary, providerNames));
    }

    private static String providerNameOf(final ProjectSummary summary, final Map<String, String> providerNames) {
        final @Nullable RunRecord run = summary.latestRun();
        final @Nullable ModelSelection model = run == null ? null : run.model();
        if (model == null) {
            return "";
        }
        final String id = model.providerId();
        return providerNames.getOrDefault(id, id);
    }

    static ProjectStatus statusOf(final ProjectSummary summary) {
        final @Nullable RunRecord run = summary.latestRun();
        if (run == null) {
            return ProjectStatus.NOT_STARTED;
        }
        if (summary.lastExport() != null) {
            return ProjectStatus.EXPORTED;
        }
        final ProjectStatus status =
                switch (run.state()) {
                    case NEW -> ProjectStatus.NOT_STARTED;
                    case RUNNING -> ProjectStatus.TRANSLATING;
                    case PAUSED -> ProjectStatus.PAUSED;
                    case CANCELLED -> ProjectStatus.STOPPED;
                    case COMPLETED -> ProjectStatus.FINISHED;
                    case FAILED -> ProjectStatus.FAILED;
                };
        log.trace("project {}: run {} reads {}", summary.project().id(), run.state(), status);
        return status;
    }

    private static ViewNames stepOf(final ProjectSummary summary) {
        if (summary.lastExport() != null) {
            return ViewNames.EXPORT;
        }
        return summary.latestRun() == null ? ViewNames.BOOK_BRIEF : ViewNames.TRANSLATING;
    }
}
