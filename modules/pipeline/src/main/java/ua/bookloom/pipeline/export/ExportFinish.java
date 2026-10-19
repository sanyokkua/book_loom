package ua.bookloom.pipeline.export;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/**
 * Decides which side files an export writes once the book is down and builds their text: the ones the person chose,
 * and the report as well whenever a source-language line or a blocking defect shipped, so such a book never leaves
 * without a file that names them.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportFinish {

    /** What the side files and the report are built from once the book is written. */
    record Written(
            EffectiveTargets targets,
            Fallbacks fallbacks,
            List<GlossaryEntry> glossary,
            @Nullable ConsistencyReport pass,
            @Nullable RunRecord lastRun) {}

    /**
     * The side files to write.
     *
     * @param contents each file's path and text
     * @param isReportForced whether the report is written though the person did not choose it; it then replaces an
     *     older report of the same book beside it, which only an earlier export of it could have left
     * @param policy how the segments without an accepted target were written
     */
    record Built(List<SideFiles.Content> contents, boolean isReportForced, ExportPolicy policy) {}

    static Built build(
            final Project project,
            final ExportRequest request,
            final Written book,
            final Fallbacks.Fallen fallen,
            final List<SuspiciousSegment> suspicious) {
        final Set<SegmentKind> kept = project.brief().alsoTranslate().keptKinds();
        final ExportPolicy policy = policyOf(project, book, fallen);
        final boolean forced = policy.isReportRequired() && !request.sideFiles().contains(SideFile.QUALITY_REPORT);
        final Set<SideFile> chosen = new HashSet<>(request.sideFiles());
        if (forced) {
            log.warn(
                    "export report written without being asked: sourceLanguage={} unresolvedBlocking={}",
                    policy.sourceLanguage().size(),
                    policy.unresolved().size());
            chosen.add(SideFile.QUALITY_REPORT);
        }
        final List<SideFiles.Content> contents = SideFiles.build(
                chosen,
                new SideFiles.Sources(
                        request.destination(),
                        book.targets(),
                        book.fallbacks().stored(),
                        kept,
                        fallen.counts(),
                        book.pass(),
                        book.glossary(),
                        suspicious,
                        policy,
                        book.lastRun()));
        return new Built(contents, forced, policy);
    }

    private static ExportPolicy policyOf(final Project project, final Written book, final Fallbacks.Fallen fallen) {
        return ExportPolicy.of(
                new ExportPolicy.Written(
                        book.targets(),
                        fallen.listed(),
                        book.fallbacks().stored(),
                        project.brief().alsoTranslate().keptKinds(),
                        SegmentLocators.of(book.fallbacks().opened())),
                project.brief().dial());
    }
}
