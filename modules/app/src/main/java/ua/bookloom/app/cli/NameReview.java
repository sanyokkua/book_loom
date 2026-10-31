package ua.bookloom.app.cli;

import com.google.inject.Inject;
import java.io.PrintStream;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The Names &amp; style screen's names work done before a headless run: the frequency scan, Review with model (its
 * verdicts and suggested targets), then the validated Accept all — every suggested target that passes the target
 * language's checks is pinned, or locked where it never inflects, and the rest stay suggestions, counted as rejected.
 * A failed step is reported and the run goes on with the glossary as it then stands: names help a
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
     * @param rejected the suggested targets the checks left out
     * @param error the step that failed, or {@code null} when every step ran
     */
    record Counts(
            int scanned,
            int removed,
            int updated,
            int suggested,
            int accepted,
            int rejected,
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
        final Result<EntryChanges<GlossaryEntry>> scanned = glossary.scan(projectId);
        if (scanned.isErr()) {
            return failed("scan", Objects.requireNonNull(scanned.error()), 0, out);
        }
        final int added = Objects.requireNonNull(scanned.data()).added().size();
        final Result<GlossaryReviewReport> reviewed = glossary.review(projectId, model, event -> {});
        if (reviewed.isErr()) {
            return failed("review", Objects.requireNonNull(reviewed.error()), added, out);
        }
        final GlossaryReviewReport report = Objects.requireNonNull(reviewed.data());
        final Result<AcceptReport> acceptance = glossary.acceptSuggested(projectId);
        if (acceptance.isErr()) {
            return failed("accept", Objects.requireNonNull(acceptance.error()), added, out);
        }
        final Counts counts = counts(added, report, Objects.requireNonNull(acceptance.data()));
        log.info("translate command names review finished counts={}", counts);
        print(counts, out);
        return counts;
    }

    private static Counts counts(int added, GlossaryReviewReport report, AcceptReport accepted) {
        return new Counts(
                added,
                report.removed(),
                report.updated(),
                report.suggested(),
                accepted.acceptedCount(),
                accepted.rejectedCount(),
                null);
    }

    private static void print(Counts counts, PrintStream out) {
        out.println("names: " + counts.scanned() + " found, " + counts.removed() + " removed, " + counts.updated()
                + " typed, " + counts.suggested() + " suggested, " + counts.accepted() + " accepted, "
                + counts.rejected() + " rejected");
    }

    private static Counts failed(String step, AppError error, int scanned, PrintStream out) {
        log.warn("translate command names {} failed code={}; the run goes on", step, error.code());
        out.println("names: " + step + " failed (" + error.title() + "); translating with the glossary as it is");
        return new Counts(scanned, 0, 0, 0, 0, 0, step + ": " + error.code());
    }
}
