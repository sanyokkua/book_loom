package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.Messages;

/**
 * The Add term card's way into the glossary: a blank source, a duplicate or a lock with no target is refused before the
 * service is asked, and a stored term joins the rows. FX thread only.
 *
 * @param glossary the port the term is added through
 * @param calls the background calls the add runs on
 * @param rows the screen's rows, where the stored term is shown
 * @param messages the catalogue a refusal is worded from
 * @param ids the source of a new entry's id
 * @param project the project the screen shows now
 */
@Slf4j
record GlossaryAdditions(
        GlossaryService glossary,
        GlossaryCalls calls,
        List<GlossaryEntry> rows,
        Messages messages,
        Supplier<String> ids,
        Supplier<String> project) {

    void add(final NewTerm term, final Runnable onAdded, final Consumer<String> onRefused) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(onAdded, "onAdded");
        Objects.requireNonNull(onRefused, "onRefused");
        final String source = term.term().strip();
        final Optional<String> refusal = term.refusal(rows, messages);
        log.debug("adding a term to project {}, refused up front: {}", project.get(), refusal.isPresent());
        if (refusal.isPresent()) {
            onRefused.accept(refusal.get());
            return;
        }
        final String target = term.target().isBlank() ? null : term.target().strip();
        final GlossaryEntry entry =
                new GlossaryEntry(ids.get(), project.get(), source, target, term.type(), term.gender(), term.locked());
        calls.run("add", () -> glossary.add(entry), answer -> {
            final AppError failure = answer.error();
            if (failure != null) {
                log.warn("adding entry {} refused with {}", entry.id(), failure.code());
                onRefused.accept(failure.message());
                return;
            }
            rows.add(Objects.requireNonNull(answer.data(), "data"));
            onAdded.run();
        });
    }
}
