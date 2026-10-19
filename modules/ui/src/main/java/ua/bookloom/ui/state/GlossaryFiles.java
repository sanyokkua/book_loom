package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The glossary's CSV import and export as the names and style screen runs them: on the background calls, each answer
 * published only while the screen's opening that asked is still current, and reported in place. FX thread only.
 *
 * @param glossary the port the files are read and written through
 * @param calls the background calls the work runs on
 * @param rows the screen's rows, which an import replaces
 * @param notice the line the screen reports in place
 * @param summary the wording of an import's or export's result
 */
@Slf4j
record GlossaryFiles(
        GlossaryService glossary,
        GlossaryCalls calls,
        ObservableList<GlossaryEntry> rows,
        ObjectProperty<@Nullable GlossaryNotice> notice,
        ImportSummary summary) {

    void importCsv(final String project, final Path source, final Consumer<Runnable> whileCurrent) {
        log.debug("importing glossary csv {} into project {}", source, project);
        calls.run(
                "import",
                () -> ImportSummary.readAfterImport(glossary, project, source),
                answer -> whileCurrent.accept(() -> {
                    final AppError failure = answer.error();
                    if (failure != null) {
                        fail("import", failure);
                        return;
                    }
                    final ImportSummary.Imported done = Objects.requireNonNull(answer.data(), "data");
                    rows.setAll(done.all());
                    notice.set(new GlossaryNotice(GlossaryNotice.Level.INFO, summary.of(done.report())));
                }));
    }

    void exportCsv(final String project, final Path destination, final Consumer<Runnable> whileCurrent) {
        log.debug("exporting the glossary of project {} to {}", project, destination);
        calls.run(
                "export",
                () -> glossary.exportCsv(project, destination),
                answer -> whileCurrent.accept(() -> {
                    final AppError failure = answer.error();
                    if (failure != null) {
                        fail("export", failure);
                        return;
                    }
                    notice.set(summary.exported(Objects.requireNonNull(answer.data(), "data")));
                }));
    }

    private void fail(final String what, final AppError failure) {
        log.warn("the glossary {} failed with {}", what, failure.code());
        notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
    }
}
