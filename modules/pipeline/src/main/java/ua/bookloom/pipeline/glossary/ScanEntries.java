package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Turns the name scan's candidates into the glossary entries it proposes, matching every candidate against the held
 * and removed terms by one key ({@link GlossaryKeys}) over the lemma and every form on both sides: a candidate is held
 * when its term or a form is an entry's lemma or form, and removed when one of them was removed. A candidate written
 * only in an oblique case ({@code Велемиром}) also matches by the nominatives it could belong to ({@link NameForms}).
 * A held entry the candidate adds forms to is listed with those forms, for the caller to store.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScanEntries {

    /**
     * What a scan found against the glossary.
     *
     * @param proposals the new entries, unlocked, with no target, type other and gender unknown, in candidate order
     * @param heldForms the forms the scan found for held entries that they do not list yet, by the held entry's term
     */
    public record Found(List<GlossaryEntry> proposals, Map<String, List<String>> heldForms) {

        /** Copies the collections. */
        public Found {
            proposals = List.copyOf(proposals);
            heldForms = Map.copyOf(heldForms);
        }
    }

    static Result<Found> of(
            final String projectId,
            final List<NameCandidate> candidates,
            final GlossaryRepository glossary,
            final List<GlossaryEntry> held,
            @Nullable final String language) {
        final Pass pass = new Pass(projectId, glossary, byKey(held));
        for (final NameCandidate candidate : candidates) {
            final Optional<AppError> failed = pass.take(candidate, namesOf(candidate, language));
            if (failed.isPresent()) {
                return Result.err(failed.get());
            }
        }
        log.debug(
                "Name proposals for project {}: {} made, {} skipped as held or a case variant already proposed, {} as"
                        + " removed, {} held entries with new forms",
                projectId,
                pass.proposals.size(),
                pass.skippedHeld,
                pass.skippedRemoved,
                pass.heldForms.size());
        return Result.ok(new Found(pass.proposals, pass.heldForms));
    }

    /** One pass over the candidates: what it proposed and skipped so far. */
    private static final class Pass {
        private final String projectId;
        private final GlossaryRepository glossary;
        private final Map<String, GlossaryEntry> heldByKey;
        private final Set<String> proposedKeys = new HashSet<>();
        private final List<GlossaryEntry> proposals = new ArrayList<>();
        private final Map<String, List<String>> heldForms = new LinkedHashMap<>();
        private int skippedHeld;
        private int skippedRemoved;

        Pass(final String projectId, final GlossaryRepository glossary, final Map<String, GlossaryEntry> heldByKey) {
            this.projectId = projectId;
            this.glossary = glossary;
            this.heldByKey = heldByKey;
        }

        Optional<AppError> take(final NameCandidate candidate, final List<String> names) {
            final Optional<GlossaryEntry> holder = names.stream()
                    .map(name -> heldByKey.get(GlossaryKeys.of(name)))
                    .filter(Objects::nonNull)
                    .findFirst();
            if (holder.isPresent() || names.stream().map(GlossaryKeys::of).anyMatch(proposedKeys::contains)) {
                holder.ifPresent(entry -> newForms(entry, candidate, heldForms));
                skippedHeld++;
                return Optional.empty();
            }
            final Result<Boolean> removed = anyRemoved(projectId, names, glossary);
            if (removed.isErr()) {
                return Optional.of(Objects.requireNonNull(removed.error(), "error"));
            }
            if (Boolean.TRUE.equals(removed.data())) {
                skippedRemoved++;
                return Optional.empty();
            }
            proposals.add(entryFor(projectId, candidate));
            names.forEach(name -> proposedKeys.add(GlossaryKeys.of(name)));
            return Optional.empty();
        }
    }

    // The lemma's key first, then the forms', so a term that is one entry's lemma and another's form finds the lemma.
    private static Map<String, GlossaryEntry> byKey(final List<GlossaryEntry> held) {
        final Map<String, GlossaryEntry> byKey = new HashMap<>();
        held.forEach(entry -> byKey.putIfAbsent(GlossaryKeys.of(entry.term()), entry));
        held.forEach(entry -> entry.forms().forEach(form -> byKey.putIfAbsent(GlossaryKeys.of(form), entry)));
        return byKey;
    }

    // The term, its forms, and, for a term that can only be an oblique case, the nominatives it could belong to.
    private static List<String> namesOf(final NameCandidate candidate, @Nullable final String language) {
        final Set<String> names = new LinkedHashSet<>();
        names.add(candidate.term());
        names.addAll(candidate.aliases());
        final Set<NameForms.Reading> readings = NameForms.candidates(candidate.term(), language);
        if (readings.stream().noneMatch(NameForms.Reading::nominative)) {
            readings.forEach(reading -> names.add(reading.lemma()));
        }
        return List.copyOf(names);
    }

    private static void newForms(
            final GlossaryEntry entry, final NameCandidate candidate, final Map<String, List<String>> heldForms) {
        final Set<String> known = new HashSet<>();
        entry.names().forEach(name -> known.add(GlossaryKeys.of(name)));
        heldForms.getOrDefault(entry.term(), List.of()).forEach(form -> known.add(GlossaryKeys.of(form)));
        final List<String> found = new ArrayList<>();
        found.add(candidate.term());
        found.addAll(candidate.aliases());
        final List<String> added =
                found.stream().filter(form -> known.add(GlossaryKeys.of(form))).toList();
        if (!added.isEmpty()) {
            heldForms.computeIfAbsent(entry.term(), _ -> new ArrayList<>()).addAll(added);
            log.trace("Held entry {} gains the forms {}", entry.term(), added);
        }
    }

    private static Result<Boolean> anyRemoved(
            final String projectId, final List<String> names, final GlossaryRepository glossary) {
        for (final String name : names) {
            final Result<Boolean> removed = glossary.wasRemoved(projectId, name);
            if (removed.isErr() || Boolean.TRUE.equals(removed.data())) {
                return removed;
            }
        }
        return Result.ok(false);
    }

    private static GlossaryEntry entryFor(final String projectId, final NameCandidate candidate) {
        final GlossaryEntry base = GlossaryEntry.of(
                GlossaryIds.of(projectId, candidate.term()),
                projectId,
                candidate.term(),
                null,
                TermType.OTHER,
                Gender.UNKNOWN,
                false);
        return new GlossaryEntry(
                base.id(),
                base.projectId(),
                base.term(),
                candidate.aliases(),
                candidate.lemmaInferred(),
                base.target(),
                base.targetState(),
                base.targetOrigin(),
                base.type(),
                base.placeKind(),
                base.typeOrigin(),
                base.gender(),
                base.genderOrigin(),
                base.genderSeedTried(),
                base.noGenderEvidence(),
                base.meaning());
    }
}
