package ua.bookloom.pipeline.export;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.qa.BlockingFindings;

/**
 * Which way each segment of the book was written when it had no accepted target of its own, so the report can say what
 * shipped as it is: a refused reply that passed the blocking checks, the machine translation of a flagged segment, or
 * the source language last. A segment with a blocking finding still on it is listed whichever way it was written.
 *
 * @param refusedReplies the locators of segments written with the model's last refused reply, in book order
 * @param machineTranslations the locators of flagged segments written with their machine translation
 * @param sourceLanguage the locators of segments written in their source language
 * @param unresolved each locator whose written text still has a blocking finding, mapped to the checks that raised them
 * @param dial the book's quality dial
 */
@Slf4j
record ExportPolicy(
        List<String> refusedReplies,
        List<String> machineTranslations,
        List<String> sourceLanguage,
        Map<String, List<String>> unresolved,
        QualityDial dial) {

    /** Copies the lists and the map. */
    ExportPolicy {
        refusedReplies = List.copyOf(refusedReplies);
        machineTranslations = List.copyOf(machineTranslations);
        sourceLanguage = List.copyOf(sourceLanguage);
        unresolved = Collections.unmodifiableMap(new LinkedHashMap<>(unresolved));
        Objects.requireNonNull(dial, "dial");
    }

    /** What the policy is built from. */
    record Written(
            EffectiveTargets targets,
            List<SourceFallback> fallbacks,
            List<SegmentRecord> records,
            Set<SegmentKind> keptKinds,
            Map<String, SegmentLocator> locators) {}

    /**
     * Sorts the written book's segments by how they were written.
     *
     * @param written the non-null parts of the written book
     * @param dial the book's quality dial
     * @return the policy; every list in book order
     */
    static ExportPolicy of(final Written written, final QualityDial dial) {
        final Set<String> inSource =
                written.fallbacks().stream().map(SourceFallback::segmentId).collect(Collectors.toSet());
        final Set<String> refused = new LinkedHashSet<>(written.targets().candidates());
        refused.removeAll(inSource);
        final ExportPolicy policy = new ExportPolicy(
                refused.stream().map(id -> locator(written, id)).toList(),
                written.records().stream()
                        .filter(record -> !record.isKeptAsSource(written.keptKinds()))
                        .filter(record -> isMachineTranslation(record, refused, inSource))
                        .map(record -> locator(written, record.segmentId()))
                        .toList(),
                written.fallbacks().stream().map(SourceFallback::locator).toList(),
                unresolved(written),
                dial);
        log.info(
                "Export policy refusedReplies={} machineTranslations={} sourceLanguage={} unresolvedBlocking={} dial={}",
                policy.refusedReplies().size(),
                policy.machineTranslations().size(),
                policy.sourceLanguage().size(),
                policy.unresolved().size(),
                dial);
        return policy;
    }

    private static Map<String, List<String>> unresolved(final Written written) {
        final Map<String, List<String>> unresolved = new LinkedHashMap<>();
        for (final SegmentRecord record : written.records()) {
            final List<String> blocking = BlockingFindings.of(record).stream()
                    .map(QaFinding::raisedBy)
                    .distinct()
                    .toList();
            if (!blocking.isEmpty()
                    && record.status() != SegmentStatus.PENDING
                    && !record.isKeptAsSource(written.keptKinds())) {
                unresolved.put(locator(written, record.segmentId()), blocking);
            }
        }
        return unresolved;
    }

    private static boolean isMachineTranslation(
            final SegmentRecord record, final Set<String> refused, final Set<String> inSource) {
        return record.status() == SegmentStatus.FLAGGED
                && record.userTarget() == null
                && record.machineTarget() != null
                && !refused.contains(record.segmentId())
                && !inSource.contains(record.segmentId());
    }

    private static String locator(final Written written, final String id) {
        final SegmentLocator locator = written.locators().get(id);
        return locator == null ? id : locator.text();
    }

    /**
     * Whether the export must leave a report whether or not the person asked for one.
     *
     * @return {@code true} if a segment was written in its source language or still holds a blocking finding
     */
    boolean isReportRequired() {
        return !sourceLanguage.isEmpty() || !unresolved.isEmpty();
    }

    /**
     * The locators of segments whose written text still holds a blocking finding.
     *
     * @return the locators in book order; empty when none does
     */
    List<String> unresolvedLocators() {
        return List.copyOf(unresolved.keySet());
    }

    /**
     * The report's sections for this policy.
     *
     * @return Markdown: the fallbacks counted per kind, the unresolved blocking findings, and the quality dial
     */
    String text() {
        return "## Fallbacks\n\n"
                + kind("Refused reply used", refusedReplies)
                + kind("Machine translation", machineTranslations)
                + kind("Source language", sourceLanguage)
                + "\n## Unresolved blocking findings\n\n"
                + (unresolved.isEmpty()
                        ? "None.\n"
                        : unresolved.entrySet().stream()
                                .map(entry -> "- " + entry.getKey() + ": " + String.join(", ", entry.getValue()) + "\n")
                                .collect(Collectors.joining()))
                + "\n## Run\n\n- Quality dial: " + dial + "\n";
    }

    private static String kind(final String label, final List<String> locators) {
        return "- " + label + ": " + locators.size()
                + (locators.isEmpty() ? "" : " (" + String.join(", ", locators) + ")") + "\n";
    }
}
