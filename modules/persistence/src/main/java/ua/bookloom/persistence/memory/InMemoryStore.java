package ua.bookloom.persistence.memory;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TmEntry;

/**
 * The shared, per-injector state every in-memory adapter reads and writes — one project map and one segment table
 * per project id, plus the single lock that keeps a chunk commit from ever being half-visible to a multi-record
 * read ({@code design.md} D2, ADR-0034).
 *
 * <p>A single-key operation ({@link ProjectSegments#byId()}'s {@code ConcurrentHashMap.compute}) needs no lock of
 * its own; only a multi-record read (first-pending, counts, flagged, by-unit, all) and a multi-record write
 * (loading a project's segments, committing a chunk) take this lock, for reading and writing respectively.
 *
 * <p>{@code @Singleton} is declared on the class itself, rather than bound in {@code PersistenceModule}, because
 * this class is package-private to {@code .memory} and so cannot be named from the {@code ua.bookloom.persistence}
 * package the module lives in; Guice's implicit (JIT) binding still honours a class-level scope annotation.
 */
@Singleton
final class InMemoryStore {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final Map<String, ProjectSegments> segmentsByProject = new ConcurrentHashMap<>();
    private final Map<String, ProjectGlossary> glossaryByProject = new ConcurrentHashMap<>();
    private final Map<String, Map<String, TmEntry>> tmByProject = new ConcurrentHashMap<>();
    private final Map<String, RollingSummary> summaryByProject = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Deferral>> deferralsByProject = new ConcurrentHashMap<>();
    private final Map<String, Map<String, RunRecord>> runsByProject = new ConcurrentHashMap<>();

    ReadWriteLock lock() {
        return lock;
    }

    Map<String, Project> projects() {
        return projects;
    }

    /**
     * Returns the segment table for a project, creating an empty one on first use so a read against an unknown
     * project answers empty rather than failing.
     */
    ProjectSegments segments(final String projectId) {
        return segmentsByProject.computeIfAbsent(projectId, id -> new ProjectSegments());
    }

    /**
     * Returns the glossary table for a project, creating an empty one on first use.
     */
    ProjectGlossary glossary(final String projectId) {
        return glossaryByProject.computeIfAbsent(projectId, id -> new ProjectGlossary());
    }

    /**
     * Returns the translation-memory table for a project, keyed {@code sourceHash + '\0' + contextKey}, creating an
     * empty one on first use.
     */
    Map<String, TmEntry> tm(final String projectId) {
        return tmByProject.computeIfAbsent(projectId, id -> new ConcurrentHashMap<>());
    }

    Map<String, RollingSummary> summaries() {
        return summaryByProject;
    }

    /**
     * Returns the deferral table for a project, keyed {@code segmentId + '\0' + reason}, creating an empty one on
     * first use.
     */
    Map<String, Deferral> deferrals(final String projectId) {
        return deferralsByProject.computeIfAbsent(projectId, id -> new ConcurrentHashMap<>());
    }

    /**
     * Returns the run table for a project, keyed by run id, creating an empty one on first use.
     */
    Map<String, RunRecord> runs(final String projectId) {
        return runsByProject.computeIfAbsent(projectId, id -> new ConcurrentHashMap<>());
    }

    /**
     * The translation-memory key one project's source hash and context key are stored under.
     */
    static String tmKey(final String sourceHash, final String contextKey) {
        return sourceHash + '\u0000' + contextKey;
    }

    /**
     * The deferral key one project's segment id, reason and waiting-on are stored under, the triple a deferral add is
     * idempotent on. A missing waiting-on is empty, so two changed terms in one segment stay two deferrals.
     */
    static String deferralKey(final String segmentId, final DeferralReason reason, @Nullable final String waitingOn) {
        return segmentId + '\u0000' + reason.name() + '\u0000' + (waitingOn == null ? "" : waitingOn);
    }

    /**
     * One project's glossary: entries by id, a lower-cased-term index for the case-insensitive duplicate/lookup
     * rule, and the set of lower-cased terms removed this session so a re-proposed name is not silently re-added.
     */
    static final class ProjectGlossary {

        private final Map<String, GlossaryEntry> byId = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<String> insertionOrder = new CopyOnWriteArrayList<>();
        private final Set<String> removedLowerTerms = ConcurrentHashMap.newKeySet();

        Map<String, GlossaryEntry> byId() {
            return byId;
        }

        Set<String> removedLowerTerms() {
            return removedLowerTerms;
        }

        Optional<GlossaryEntry> findByLowerTerm(final String lowerTerm) {
            return byId.values().stream()
                    .filter(entry -> entry.term().toLowerCase(Locale.ROOT).equals(lowerTerm))
                    .findFirst();
        }

        void put(final GlossaryEntry entry) {
            if (byId.putIfAbsent(entry.id(), entry) != null) {
                byId.put(entry.id(), entry);
            } else {
                insertionOrder.add(entry.id());
            }
        }

        Optional<GlossaryEntry> removeById(final String entryId) {
            final GlossaryEntry removed = byId.remove(entryId);
            if (removed != null) {
                insertionOrder.remove(entryId);
            }
            return Optional.ofNullable(removed);
        }

        List<GlossaryEntry> allInInsertionOrder() {
            final List<GlossaryEntry> result = new ArrayList<>(insertionOrder.size());
            for (final String id : insertionOrder) {
                final GlossaryEntry entry = byId.get(id);
                if (entry != null) {
                    result.add(entry);
                }
            }
            return List.copyOf(result);
        }
    }

    /**
     * One project's segment records: a concurrency-safe by-id map for atomic per-key updates, and a separately held
     * document-order id list — replaced wholesale on {@link #replaceAll}, read without locking otherwise, since the
     * order of ids never changes between one {@code saveAll}/commit and the next.
     */
    static final class ProjectSegments {

        private volatile List<String> orderedIds = List.of();
        private final Map<String, SegmentRecord> byId = new ConcurrentHashMap<>();

        List<String> orderedIds() {
            return orderedIds;
        }

        Map<String, SegmentRecord> byId() {
            return byId;
        }

        /**
         * Replaces every record, recording the given list's order as this project's document order.
         *
         * @param records the segment records in document order
         */
        void replaceAll(final List<SegmentRecord> records) {
            byId.clear();
            final List<String> ids = new ArrayList<>(records.size());
            for (final SegmentRecord record : records) {
                byId.put(record.segmentId(), record);
                ids.add(record.segmentId());
            }
            orderedIds = List.copyOf(ids);
        }
    }
}
