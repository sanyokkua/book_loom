package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * Puts a project's glossary back as it was before a step of many writes failed half-way, since the storage port has
 * no transaction that spans them: every entry the step left that was not there goes, every one that was is written
 * back as it was. Best effort — a write that fails here is logged and the next one is still tried.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryRestore {

    static void restore(final GlossaryRepository glossary, final String projectId, final List<GlossaryEntry> before) {
        final Set<String> ids = new HashSet<>();
        before.forEach(entry -> ids.add(entry.id()));
        final List<GlossaryEntry> now = glossary.all(projectId).data();
        if (now != null) {
            now.stream()
                    .filter(entry -> !ids.contains(entry.id()))
                    .forEach(entry -> logIfFailed("remove", glossary.remove(projectId, entry.id())));
        }
        before.forEach(entry -> {
            final Result<GlossaryEntry> back = glossary.update(entry);
            if (back.isErr()) {
                logIfFailed("add back", glossary.add(entry));
            }
        });
        log.debug("Glossary of project={} restored to {} entries", projectId, before.size());
    }

    private static void logIfFailed(final String what, final Result<?> done) {
        if (done.isErr()) {
            log.warn(
                    "Glossary restore could not {}: {}",
                    what,
                    done.error() == null ? null : done.error().code());
        }
    }
}
