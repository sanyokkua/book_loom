package ua.bookloom.pipeline.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.FindingKind;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.typography.Normalisation;
import ua.bookloom.pipeline.typography.TypographyNormalizer;

/**
 * A project's stored decisions laid over its opened book, so the writer sees, per segment, the text the file is to
 * hold: the person's edit, else the machine's, else the source. A PENDING record, a FLAGGED record no draft ever passed
 * for, and a record kept as source by choice all keep their source.
 *
 * @param document the opened book with each written target set on its segment
 * @param maskedTargets each segment written with a target mapped to that target's masked form — the order its
 *     placeholders were written in, which the re-open check compares; a segment written in its source is absent
 * @param sourceFallbacks the segments written in their source although their record holds a target, because that
 *     target's placeholders no longer match the segment's, in book order
 * @param noTarget the FLAGGED segments written in their source because no draft ever passed the gates and no refused
 *     reply was fit to write, in book order
 * @param candidates the FLAGGED segments with no target of their own written with the model's last refused reply,
 *     because it passes every blocking check, in book order
 */
@Slf4j
record EffectiveTargets(
        Document document,
        Map<String, String> maskedTargets,
        List<String> sourceFallbacks,
        List<String> noTarget,
        List<String> candidates) {

    /** Copies the map and the list so the targets cannot change after construction. */
    EffectiveTargets {
        Objects.requireNonNull(document, "document");
        maskedTargets = Collections.unmodifiableMap(new LinkedHashMap<>(maskedTargets));
        sourceFallbacks = List.copyOf(sourceFallbacks);
        noTarget = List.copyOf(noTarget);
        candidates = List.copyOf(candidates);
    }

    /** Targets with no refused reply written. */
    EffectiveTargets(
            final Document document,
            final Map<String, String> maskedTargets,
            final List<String> sourceFallbacks,
            final List<String> noTarget) {
        this(document, maskedTargets, sourceFallbacks, noTarget, List.of());
    }

    /**
     * Decides each segment's written text.
     *
     * @param opened the non-null book as the project holds it open
     * @param records the non-null stored records of the project
     * @param keptKinds the auxiliary kinds the brief keeps as source when the export starts; never null
     * @param unmask restores a masked target into a segment's markup, failing when the target's placeholders no longer
     *     match the segment's; never null
     * @param sourceLanguage the source language tag the blocking checks of a refused reply need, or null when none is
     *     declared
     * @param targetLanguage the language tag the export writes; a person's edit is normalised for it, because an edit
     *     does not pass through the run's typography gate; never null
     * @return the book to write, the masked form of every target written, and the targets written as source instead
     */
    static EffectiveTargets apply(
            final Document opened,
            final List<SegmentRecord> records,
            final Set<SegmentKind> keptKinds,
            final BiFunction<Segment, String, Result<String>> unmask,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(keptKinds, "keptKinds");
        Objects.requireNonNull(unmask, "unmask");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final Map<String, SegmentRecord> byId =
                records.stream().collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (a, b) -> a));
        log.debug(
                "Applying effective targets records={} document={} keptKinds={}",
                records.size(),
                opened.id(),
                keptKinds);
        final Decision decision = Decision.fresh(
                keptKinds,
                unmask,
                new Languages(sourceLanguage, targetLanguage),
                TitleConsistency.of(opened, byId, keptKinds, targetLanguage));
        final List<Unit> units = decideAll(opened, byId, decision);
        logApplied(opened, decision);
        return new EffectiveTargets(
                opened.withUnits(units),
                decision.masked(),
                decision.fallbacks(),
                decision.noTarget(),
                decision.candidates());
    }

    private static void logApplied(final Document opened, final Decision decision) {
        log.debug(
                "Effective targets applied document={} writtenWithTarget={} sourceFallbacks={} noTarget={} candidates={}",
                opened.id(),
                decision.masked().size(),
                decision.fallbacks(),
                decision.noTarget(),
                decision.candidates());
    }

    private static List<Unit> decideAll(
            final Document opened, final Map<String, SegmentRecord> byId, final Decision decision) {
        return opened.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> decide(segment, byId.get(segment.id()), decision))
                        .toList()))
                .toList();
    }

    /** The pair a refused reply is checked in. */
    private record Languages(@Nullable String source, String target) {}

    /** What deciding one segment reads and fills. */
    private record Decision(
            Set<SegmentKind> keptKinds,
            BiFunction<Segment, String, Result<String>> unmask,
            Languages languages,
            TitleConsistency titles,
            Map<String, String> masked,
            List<String> fallbacks,
            List<String> noTarget,
            List<String> candidates) {

        static Decision fresh(
                final Set<SegmentKind> keptKinds,
                final BiFunction<Segment, String, Result<String>> unmask,
                final Languages languages,
                final TitleConsistency titles) {
            return new Decision(
                    keptKinds,
                    unmask,
                    languages,
                    titles,
                    new LinkedHashMap<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>());
        }
    }

    private static Segment decide(
            final Segment segment, @Nullable final SegmentRecord record, final Decision decision) {
        if (record == null || record.isKeptAsSource(decision.keptKinds())) {
            log.trace("segment={} written as source: {}", segment.id(), record == null ? "no record" : "kept");
            return segment;
        }
        final Optional<TitleConsistency.Replacement> title = decision.titles().forSegment(segment, record);
        if (title.isPresent()) {
            return titled(segment, record, title.get(), decision);
        }
        final String target = writtenTarget(record);
        if (target == null) {
            return decideWithoutTarget(segment, record, decision);
        }
        final String maskedTarget =
                record.userTarget() != null ? record.maskedUserTarget() : record.maskedMachineTarget();
        if (maskedTarget == null
                || decision.unmask().apply(segment, maskedTarget).isErr()) {
            log.warn(
                    "segment={} status={} written as source: its target's placeholders do not match the segment's",
                    segment.id(),
                    record.status());
            decision.fallbacks().add(segment.id());
            return segment;
        }
        if (record.userTarget() != null) {
            return decideEdited(segment, record, maskedTarget, target, decision);
        }
        decision.masked().put(segment.id(), maskedTarget);
        return segment.withDecision(record.status(), target);
    }

    private static Segment titled(
            final Segment segment,
            final SegmentRecord record,
            final TitleConsistency.Replacement title,
            final Decision decision) {
        log.debug("segment={} takes the book's one translated title or author", segment.id());
        decision.masked().put(segment.id(), title.masked());
        return segment.withDecision(record.status(), title.target());
    }

    // The order for a segment with no target of its own: the model's refused reply when it passes every blocking check,
    // else the source. (A flagged segment's machine translation is its target, so it never reaches here.) Only the last
    // refused reply is stored, so there is no better candidate to pick among.
    private static Segment decideWithoutTarget(
            final Segment segment, final SegmentRecord record, final Decision decision) {
        if (record.status() != SegmentStatus.FLAGGED) {
            log.trace("segment={} status={} written as source: no target", segment.id(), record.status());
            return segment;
        }
        final Segment candidate = candidate(segment, record, decision);
        if (candidate != null) {
            return candidate;
        }
        log.debug("segment={} export fallback=source-language: no target and no fit refused reply", segment.id());
        decision.noTarget().add(segment.id());
        return segment;
    }

    private static @Nullable Segment candidate(
            final Segment segment, final SegmentRecord record, final Decision decision) {
        final String rejected = record.rejectedTarget();
        if (rejected == null || rejected.isBlank()) {
            return null;
        }
        final Result<String> restored = decision.unmask().apply(segment, rejected);
        if (restored.isErr()) {
            log.debug("segment={} refused reply not fit: its placeholders do not match", segment.id());
            return null;
        }
        final String shown = DisplayText.of(rejected);
        final String origin = DisplayText.of(segment.masked());
        final List<FindingKind> blocking = blockingKinds(origin, shown, decision.languages());
        if (shown.equals(origin) || !blocking.isEmpty()) {
            log.debug(
                    "segment={} refused reply not fit: echo={} blocking={}",
                    segment.id(),
                    shown.equals(origin),
                    blocking);
            return null;
        }
        log.debug("segment={} export fallback=refused-reply-used", segment.id());
        decision.masked().put(segment.id(), rejected);
        decision.candidates().add(segment.id());
        return segment.withDecision(record.status(), Objects.requireNonNull(restored.data()));
    }

    private static List<FindingKind> blockingKinds(final String origin, final String shown, final Languages languages) {
        return TextChecks.run(origin, shown, languages.source(), languages.target()).stream()
                .filter(CheckFinding::blocking)
                .map(CheckFinding::kind)
                .toList();
    }

    private static @Nullable String writtenTarget(final SegmentRecord record) {
        return switch (record.status()) {
            case ACCEPTED, REVISED, FLAGGED -> record.effectiveTarget().orElse(null);
            case PENDING -> null;
        };
    }

    // A person's edit skips the run's typography gate, so it is normalised here; the stored target is kept when the
    // normalised form would not restore, so typography never costs a segment its translation.
    private static Segment decideEdited(
            final Segment segment,
            final SegmentRecord record,
            final String maskedTarget,
            final String target,
            final Decision decision) {
        final Normalisation normalised = TypographyNormalizer.normalise(
                segment.masked(), maskedTarget, decision.languages().target());
        final Result<String> restored = decision.unmask().apply(segment, normalised.text());
        if (!normalised.isChanged() || restored.isErr()) {
            decision.masked().put(segment.id(), maskedTarget);
            return segment.withDecision(record.status(), target);
        }
        log.debug("segment={} edited target normalised at export: {}", segment.id(), normalised.note());
        decision.masked().put(segment.id(), normalised.text());
        return segment.withDecision(record.status(), Objects.requireNonNull(restored.data()));
    }
}
