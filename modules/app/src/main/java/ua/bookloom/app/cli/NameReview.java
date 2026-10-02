package ua.bookloom.app.cli;

import com.google.inject.Inject;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The Names &amp; style screen's names work done before a headless run: the frequency scan, Review with model (its
 * verdicts and suggested targets), then Accept all — every suggested target confirmed as the person's, so the run
 * applies it exactly. A failed step is reported and the run goes on with the glossary as it then stands: names help a
 * translation, they are not needed for one.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
final class NameReview {

    /**
     * What the review did.
     *
     * @param scanned the terms the scan added
     * @param removed the terms the model judged not a name
     * @param updated the terms that took the model's type or gender
     * @param suggested the terms that received a suggested target
     * @param accepted the suggested targets confirmed
     * @param error the step that failed, or {@code null} when every step ran
     */
    record Counts(
            int scanned,
            int removed,
            int updated,
            int suggested,
            int accepted,
            @Nullable String error) {}

    private final GlossaryService glossary;

    /**
     * Scans, reviews and accepts the project's names.
     *
     * @param projectId the project; never null
     * @param model the run's model; never null
     * @param out where the one summary line goes; never null
     * @return what was done; never null
     */
    Counts review(String projectId, ChatModel model, PrintStream out) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        log.info("translate command names review started project={}", projectId);
        final Result<List<GlossaryEntry>> scanned = glossary.scan(projectId);
        if (scanned.isErr()) {
            return failed("scan", Objects.requireNonNull(scanned.error()), 0, out);
        }
        final int added = Objects.requireNonNull(scanned.data()).size();
        final Result<GlossaryReviewReport> reviewed = glossary.review(projectId, model, event -> {});
        if (reviewed.isErr()) {
            return failed("review", Objects.requireNonNull(reviewed.error()), added, out);
        }
        final GlossaryReviewReport report = Objects.requireNonNull(reviewed.data());
        final int accepted = acceptAll(report.entries());
        final Counts counts = new Counts(added, report.removed(), report.updated(), report.suggested(), accepted, null);
        log.info("translate command names review finished counts={}", counts);
        out.println("names: " + added + " found, " + report.removed() + " removed, " + report.updated() + " typed, "
                + report.suggested() + " suggested, " + accepted + " accepted");
        return counts;
    }

    private int acceptAll(List<GlossaryEntry> entries) {
        int accepted = 0;
        for (final GlossaryEntry entry : entries) {
            if (entry.isSuggested() && glossary.update(entry.accepted()).isOk()) {
                accepted++;
            }
        }
        log.debug("translate command accepted suggested targets count={}", accepted);
        return accepted;
    }

    private static Counts failed(String step, AppError error, int scanned, PrintStream out) {
        log.warn("translate command names {} failed code={}; the run goes on", step, error.code());
        out.println("names: " + step + " failed (" + error.title() + "); translating with the glossary as it is");
        return new Counts(scanned, 0, 0, 0, 0, step + ": " + error.code());
    }
}
