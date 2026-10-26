package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * One person, one gender (15i.N2): the gender known for one spelling of a person is written to the others
 * ({@link NameClusters}). Nothing the person set is ever overwritten, and the person's own choice, made for any
 * spelling, becomes theirs for every other spelling whose gender is not already theirs.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClusterGender {

    /**
     * Gives the other spellings of a person the gender the person just chose for one of them.
     *
     * @param glossary the glossary to read and update
     * @param chosen the entry as stored after the person's edit
     * @param before the entry as it was held, or null for a new entry
     * @param languageTag the source language's tag, or null when it is not known
     * @return how many other entries changed, or the repository's error
     */
    static Result<Integer> propagate(
            final GlossaryRepository glossary,
            final GlossaryEntry chosen,
            @Nullable final GlossaryEntry before,
            @Nullable final String languageTag) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(chosen, "chosen");
        return propagateAll(glossary, chosen.projectId(), List.of(new EntryChange(before, chosen)), languageTag);
    }

    /**
     * As {@link #propagate} for many entries stored at once, in their order: the glossary is read once, and only the
     * spellings that change are written, each in its own atomic step so a change made meanwhile is not overwritten. A
     * spelling the person's gender already reached keeps it, so the first choice of a person wins over a later one, as
     * one call after another would leave it.
     *
     * @param glossary the glossary to read and update
     * @param projectId the project
     * @param changes the entries as held before and as stored after, in the order they were made
     * @param languageTag the source language's tag, or null when it is not known
     * @return how many other entries changed, or the repository's error
     */
    static Result<Integer> propagateAll(
            final GlossaryRepository glossary,
            final String projectId,
            final List<EntryChange> changes,
            @Nullable final String languageTag) {
        final List<GlossaryEntry> choices = changes.stream()
                .filter(ClusterGender::isChoice)
                .map(EntryChange::after)
                .toList();
        log.debug("Cluster gender project={}: {} of {} entries are a person's gender choice",
                projectId, choices.size(), changes.size());
        if (choices.isEmpty()) {
            return Result.ok(0);
        }
        return glossary.all(projectId).flatMap(held -> {
            final NameClusters clusters = NameClusters.of(held, null, languageTag);
            final Map<String, GlossaryEntry> current = new LinkedHashMap<>();
            held.forEach(entry -> current.put(entry.id(), entry));
            final Map<String, GlossaryEntry> receivers = new LinkedHashMap<>();
            choices.forEach(chosen -> share(clusters, current, receivers, chosen));
            return writeAll(glossary, projectId, List.copyOf(receivers.values()));
        });
    }

    private static boolean isChoice(final EntryChange change) {
        final GlossaryEntry chosen = change.after();
        final GlossaryEntry before = change.before();
        final boolean choice = chosen.gender() != Gender.UNKNOWN
                && chosen.genderOrigin() == GenderOrigin.PERSON
                && (before == null
                        || before.gender() != chosen.gender()
                        || before.genderOrigin() != GenderOrigin.PERSON);
        log.debug("Cluster gender: entry {} is a person's gender choice: {}", chosen.id(), choice);
        return choice;
    }

    // Each receiver is written with the gender it was given, applied to the entry as held at that instant.
    private static Result<Integer> writeAll(
            final GlossaryRepository glossary, final String projectId, final List<GlossaryEntry> receivers) {
        for (final GlossaryEntry receiver : receivers) {
            final Result<Optional<GlossaryEntry>> stored =
                    glossary.update(projectId, receiver.term(), held -> asPerson(held, receiver.gender()));
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
        }
        return Result.ok(receivers.size());
    }

    // The other spellings of the chosen one's person that no person's gender holds yet take the chosen gender.
    private static void share(
            final NameClusters clusters,
            final Map<String, GlossaryEntry> current,
            final Map<String, GlossaryEntry> receivers,
            final GlossaryEntry chosen) {
        for (final GlossaryEntry member : clusters.membersOf(chosen)) {
            final GlossaryEntry now = current.get(member.id());
            if (member.id().equals(chosen.id()) || now == null || now.genderOrigin() == GenderOrigin.PERSON) {
                continue;
            }
            final GlossaryEntry changed = asPerson(now, chosen.gender());
            if (changed.equals(now)) {
                continue;
            }
            current.put(changed.id(), changed);
            receivers.put(changed.id(), changed);
        }
    }

    /**
     * Gives every spelling of each person the one gender the glossary knows for the person: the person's, else the
     * model's or the book's verified, else a suggested one.
     *
     * @param glossary the glossary to read and update
     * @param projectId the project
     * @param languageTag the source language's tag, or null when it is not known
     * @return how many entries changed, or the repository's error
     */
    static Result<Integer> reconcile(
            final GlossaryRepository glossary, final String projectId, @Nullable final String languageTag) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(projectId, "projectId");
        return glossary.all(projectId).flatMap(held -> {
            final NameClusters clusters = NameClusters.of(held, null, languageTag);
            final Set<String> done = new HashSet<>();
            int changed = 0;
            for (final GlossaryEntry entry : held) {
                final List<GlossaryEntry> members = clusters.membersOf(entry);
                if (members.size() < 2 || !done.add(members.getFirst().id())) {
                    continue;
                }
                final Result<Integer> written = share(glossary, projectId, members);
                if (written.isErr()) {
                    return written;
                }
                changed += Objects.requireNonNull(written.data(), "written");
            }
            log.info("Cluster gender project={} entriesChanged={}", projectId, changed);
            return Result.ok(changed);
        });
    }

    // The highest-ranked known gender of a cluster, when its holders agree, goes to every member not already holding
    // it at that rank or a higher one.
    private static Result<Integer> share(
            final GlossaryRepository glossary, final String projectId, final List<GlossaryEntry> members) {
        for (final GenderOrigin rank : GenderOrigin.values()) {
            final Set<Gender> held = new HashSet<>();
            members.stream()
                    .filter(member -> member.genderOrigin() == rank)
                    .forEach(member -> held.add(member.gender()));
            if (held.isEmpty()) {
                continue;
            }
            if (held.size() > 1) {
                log.debug("Cluster gender: members disagree at rank {}: left alone", rank);
                return Result.ok(0);
            }
            final Gender gender = held.iterator().next();
            final List<GlossaryEntry> receivers = members.stream()
                    .filter(member -> member.genderOrigin() == null
                            || member.genderOrigin().compareTo(rank) > 0)
                    .toList();
            log.debug("Cluster gender: {} at rank {} goes to {} members", gender, rank, receivers.size());
            return write(glossary, projectId, receivers, current -> atRank(current, gender, rank));
        }
        return Result.ok(0);
    }

    private static Result<Integer> write(
            final GlossaryRepository glossary,
            final String projectId,
            final List<GlossaryEntry> receivers,
            final UnaryOperator<GlossaryEntry> change) {
        int changed = 0;
        for (final GlossaryEntry receiver : receivers) {
            if (change.apply(receiver).equals(receiver)) {
                continue;
            }
            final Result<Optional<GlossaryEntry>> stored = glossary.update(projectId, receiver.term(), change);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            changed++;
        }
        return Result.ok(changed);
    }

    private static GlossaryEntry asPerson(final GlossaryEntry current, final Gender gender) {
        return current.genderOrigin() == GenderOrigin.PERSON ? current : current.withGender(gender);
    }

    private static GlossaryEntry atRank(final GlossaryEntry current, final Gender gender, final GenderOrigin rank) {
        if (current.genderOrigin() != null && current.genderOrigin().compareTo(rank) <= 0) {
            return current;
        }
        return switch (rank) {
            case PERSON -> current.withGender(gender);
            case VERIFIED -> ReviewCommit.verified(current, gender);
            case SUGGESTED -> current.withSuggestedGender(gender);
        };
    }
}
