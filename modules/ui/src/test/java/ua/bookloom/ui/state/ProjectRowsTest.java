package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ViewNames;

/** How stored projects are grouped under their book and what status, progress and step each row gets. */
class ProjectRowsTest {

    private static final Map<String, String> PROVIDERS = Map.of("ollama", "Ollama");
    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");

    private static Project project(final String id, final String hash, final String target, final String file) {
        return ProjectFixtures.project(
                id, file, hash, "en", target, NameOrigin.AUTO, "Earth Gravity · " + target, OPENED);
    }

    private static ProjectSummary summary(
            final Project project,
            final int decided,
            final @Nullable RunRecord run,
            final @Nullable ExportRecord export) {
        return ProjectFixtures.summary(project, decided, run, export);
    }

    private static ProjectSummary of(final @Nullable JobState state, final boolean exported) {
        final Project project = project("p1", ProjectFixtures.BOOK_HASH, "uk", ProjectFixtures.BOOK_FILE);
        return summary(
                project,
                0,
                state == null ? null : ProjectFixtures.run("p1", state),
                exported ? ProjectFixtures.export(OPENED) : null);
    }

    private static ProjectRow onlyRow(final ProjectSummary summary, final @Nullable String openId) {
        final List<BookGroup> groups = ProjectRows.of(List.of(summary), openId, PROVIDERS);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().rows()).hasSize(1);
        return groups.getFirst().rows().getFirst();
    }

    // IF translations of one book were listed as separate books, THEN "Translate into another language" would have no
    // header to sit on and the person could not see the book has two translations.
    @Test
    void of_twoPairsOfOneBookAndAnotherBook_groupsByContentHashInListOrder() {
        final ProjectSummary uk = summary(project("p-uk", "hash-a", "uk", "earth-gravity.epub"), 10, null, null);
        final ProjectSummary other = summary(project("p-other", "hash-b", "uk", "tide-tables.epub"), 0, null, null);
        final ProjectSummary de = summary(project("p-de", "hash-a", "de", "earth-gravity.epub"), 0, null, null);

        final List<BookGroup> groups = ProjectRows.of(List.of(uk, other, de), null, PROVIDERS);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).contentHash()).isEqualTo("hash-a");
        assertThat(groups.get(0).fileName()).isEqualTo("earth-gravity.epub");
        assertThat(groups.get(0).format()).isEqualTo(BookFormat.EPUB);
        assertThat(groups.get(0).totalSegments()).isEqualTo(ProjectFixtures.BOOK_SEGMENTS);
        assertThat(groups.get(0).rows())
                .extracting(row -> row.summary().project().id())
                .containsExactly("p-uk", "p-de");
        assertThat(groups.get(1).contentHash()).isEqualTo("hash-b");
        assertThat(groups.get(1).rows()).hasSize(1);
    }

    @Test
    void of_noProjects_isEmpty() {
        assertThat(ProjectRows.of(List.of(), null, PROVIDERS)).isEmpty();
    }

    // IF the percent were rounded up or taken of the wrong total, THEN a book 633 of 1240 decided would not read 51.
    @Test
    void of_decidedOfTotal_isThePercentOfStoredSegments() {
        final ProjectSummary half = summary(
                project("p1", ProjectFixtures.BOOK_HASH, "uk", ProjectFixtures.BOOK_FILE),
                633,
                ProjectFixtures.run("p1", JobState.PAUSED),
                null);

        assertThat(onlyRow(half, null).percent()).isEqualTo(51);
    }

    // IF a project with no stored segments divided by zero, THEN the whole list would fail to show.
    @Test
    void of_noSegments_isZeroPercent() {
        final Project project = project("p1", ProjectFixtures.BOOK_HASH, "uk", ProjectFixtures.BOOK_FILE);

        final ProjectRow row = onlyRow(new ProjectSummary(project, 0, 0, null, null), null);

        assertThat(row.percent()).isZero();
    }

    // IF the status chip read a state the stores do not hold, THEN the row would promise what a run did not do.
    @ParameterizedTest
    @CsvSource({
        "NONE,  false, NOT_STARTED",
        "RUNNING, false, TRANSLATING",
        "PAUSED, false, PAUSED",
        "CANCELLED, false, STOPPED",
        "COMPLETED, false, FINISHED",
        "FAILED, false, FAILED",
        "COMPLETED, true, EXPORTED",
        "FAILED, true, EXPORTED",
    })
    void of_latestRunAndExport_decideTheStatus(
            final String state, final boolean exported, final ProjectStatus expected) {
        final ProjectSummary summary = of("NONE".equals(state) ? null : JobState.valueOf(state), exported);

        assertThat(onlyRow(summary, null).status()).isEqualTo(expected);
    }

    // IF a run whose window is gone read as still translating, or as stopped by the person, THEN a crash would be hidden
    // behind "Translating" or "Stopped"; a run the window counts as interrupted reads "Interrupted".
    @ParameterizedTest
    @CsvSource({"RUNNING", "PAUSED", "CANCELLED"})
    void of_runCountedAsInterrupted_readsInterrupted(final JobState state) {
        final ProjectSummary summary = of(state, false);

        final ProjectRow row = ProjectRows.of(List.of(summary), null, PROVIDERS, run -> true)
                .getFirst()
                .rows()
                .getFirst();

        assertThat(row.status()).isEqualTo(ProjectStatus.INTERRUPTED);
    }

    // IF Continue opened the wrong step, THEN a finished book would reopen on Names & style, or a new one on Export.
    @ParameterizedTest
    @CsvSource({
        "NONE, false, BOOK_BRIEF",
        "RUNNING, false, TRANSLATING",
        "COMPLETED, false, TRANSLATING",
        "FAILED, false, TRANSLATING",
        "COMPLETED, true, EXPORT",
    })
    void of_latestRunAndExport_decideTheStepTheRowOpens(
            final String state, final boolean exported, final ViewNames expected) {
        final ProjectSummary summary = of("NONE".equals(state) ? null : JobState.valueOf(state), exported);

        assertThat(onlyRow(summary, null).step()).isEqualTo(expected);
    }

    // IF the open project were not marked, THEN its row would offer Continue for what is already on screen.
    @Test
    void of_openProjectId_marksOnlyThatRowOpen() {
        final ProjectSummary uk = summary(project("p-uk", "hash-a", "uk", "earth-gravity.epub"), 0, null, null);
        final ProjectSummary de = summary(project("p-de", "hash-a", "de", "earth-gravity.epub"), 0, null, null);

        final List<ProjectRow> rows =
                ProjectRows.of(List.of(uk, de), "p-de", PROVIDERS).getFirst().rows();

        assertThat(rows).extracting(ProjectRow::open).containsExactly(false, true);
    }

    @Test
    void of_openProjectNotListed_marksNoRow() {
        final ProjectSummary uk = summary(project("p-uk", "hash-a", "uk", "earth-gravity.epub"), 0, null, null);

        final List<ProjectRow> rows =
                ProjectRows.of(List.of(uk), "gone", PROVIDERS).getFirst().rows();

        assertThat(rows).extracting(ProjectRow::open).containsExactly(false);
    }

    // IF a status carried another status's words or colour, THEN a failed run would look finished.
    @ParameterizedTest
    @CsvSource({
        "NOT_STARTED, PROJECTS_STATUS_NOT_STARTED",
        "TRANSLATING, PROJECTS_STATUS_TRANSLATING",
        "FINISHED, PROJECTS_STATUS_FINISHED",
        "EXPORTED, PROJECTS_STATUS_EXPORTED",
        "FAILED, PROJECTS_STATUS_FAILED",
        "PAUSED, PROJECTS_STATUS_PAUSED",
        "STOPPED, PROJECTS_STATUS_STOPPED",
    })
    void messageKey_eachStatus_isItsOwnWords(
            final ProjectStatus status, final ua.bookloom.ui.i18n.MessageKey expected) {
        assertThat(status.messageKey()).isEqualTo(expected);
    }

    // IF an idle project's chip were the working blue, THEN a paused or stopped book would look as if it translated.
    @ParameterizedTest
    @CsvSource({
        "NOT_STARTED, WAITING",
        "PAUSED, WAITING",
        "STOPPED, WAITING",
        "TRANSLATING, WORKING",
        "FINISHED, DONE",
        "EXPORTED, DONE",
        "FAILED, FAILED",
    })
    void meaning_eachStatus_isItsOwnColour(
            final ProjectStatus status, final ua.bookloom.ui.control.StateMarker.Meaning expected) {
        assertThat(status.meaning()).isEqualTo(expected);
    }
}
