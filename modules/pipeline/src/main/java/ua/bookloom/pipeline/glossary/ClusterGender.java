package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.List;
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
        final boolean choice = chosen.gender() != Gender.UNKNOWN
                && chosen.genderOrigin() == GenderOrigin.PERSON
                && (before == null
                        || before.gender() != chosen.gender()
                        || before.genderOrigin() != GenderOrigin.PERSON);
        log.debug("Cluster gender: entry {} is a person's gender choice: {}", chosen.id(), choice);
        if (!choice) {
            return Result.ok(0);
        }
        return glossary.all(chosen.projectId()).flatMap(held -> {
            final List<GlossaryEntry> others = NameClusters.of(held, null, languageTag).membersOf(chosen).stream()
                    .filter(member -> !member.id().equals(chosen.id()))
                    .filter(member -> member.genderOrigin() != GenderOrigin.PERSON)
                    .toList();
            return write(glossary, chosen.projectId(), others, current -> asPerson(current, chosen.gender()));
        });
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
            case VERIFIED -> current.withInferredGender(gender);
            case SUGGESTED -> current.withSuggestedGender(gender);
        };
    }
}
