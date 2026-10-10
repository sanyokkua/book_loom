package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The glossary's translate-only action: the suggested-targets call over the entries a model may still change, with no
 * verdict on whether a term is a name and no type or gender change, so the person can ask for renderings alone.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryTranslation {

    static Result<EntryChanges<GlossaryEntry>> run(
            final GlossaryRepository glossary,
            final GlossaryModelScans modelScans,
            final String projectId,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        return glossary.all(projectId).flatMap(before -> {
            final List<GlossaryEntry> open =
                    before.stream().filter(TermReview::isOpen).toList();
            log.info("Glossary translate started project={} held={} open={}", projectId, before.size(), open.size());
            if (open.isEmpty()) {
                return Result.ok(EntryChanges.<GlossaryEntry>none());
            }
            return modelScans
                    .suggestOnto(projectId, open, model, progress)
                    .flatMap(answered -> write(glossary, projectId, open, answered))
                    .flatMap(written -> glossary.all(projectId))
                    .map(after -> changes(projectId, before, after));
        });
    }

    // Each suggestion goes onto the entry as it is now, so a target the person typed while the model thought stays.
    private static Result<Boolean> write(
            final GlossaryRepository glossary,
            final String projectId,
            final List<GlossaryEntry> asked,
            final List<GlossaryEntry> answered) {
        final Result<List<GlossaryEntry>> current = glossary.all(projectId);
        if (current.isErr()) {
            return Result.err(Objects.requireNonNull(current.error(), "error"));
        }
        final Map<String, GlossaryEntry> byId = new HashMap<>();
        Objects.requireNonNull(current.data(), "current").forEach(entry -> byId.put(entry.id(), entry));
        for (int i = 0; i < asked.size(); i++) {
            final String target = answered.get(i).target();
            final GlossaryEntry now = byId.get(asked.get(i).id());
            if (target == null || target.isBlank() || now == null || !TermReview.isOpen(now)) {
                log.debug(
                        "Glossary translate leaves entry {} alone", asked.get(i).id());
                continue;
            }
            final Result<GlossaryEntry> stored = glossary.update(now.withSuggestedTarget(target));
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
        }
        return Result.ok(true);
    }

    private static EntryChanges<GlossaryEntry> changes(
            final String projectId, final List<GlossaryEntry> before, final List<GlossaryEntry> after) {
        final EntryChanges<GlossaryEntry> changes =
                EntryChanges.between(before, after, GlossaryEntry::id, entry -> null);
        log.info("Glossary translate finished project={} changed={}", projectId, changes.size());
        return changes;
    }
}
