package ua.bookloom.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * A project's glossary by the {@link GlossaryKeys} key of every lemma and every form its entries list, built once
 * per load, so both adapters match a term to an entry the same way: a lemma first, else the first entry (in the
 * glossary's order) that lists the term as a form. Shared by the in-memory and the SQLite adapters; confined to the
 * one operation that built it.
 */
@Slf4j
public final class GlossaryIndex {

    private final Map<String, GlossaryEntry> byLemma = new HashMap<>();
    private final Map<String, GlossaryEntry> byForm = new HashMap<>();

    private GlossaryIndex() {}

    /**
     * The index of some entries.
     *
     * @param entries the non-null entries in the glossary's order
     * @return the index; never null
     */
    public static GlossaryIndex of(final Collection<GlossaryEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        final GlossaryIndex index = new GlossaryIndex();
        entries.forEach(index::add);
        log.debug("Glossary index built entries={} formKeys={}", index.byLemma.size(), index.byForm.size());
        return index;
    }

    /**
     * Every key an entry is known by.
     *
     * @param entry the non-null entry
     * @return its lemma's key first, then its forms' keys; never null or empty
     */
    public static List<String> keysOf(final GlossaryEntry entry) {
        final List<String> keys = new ArrayList<>(entry.forms().size() + 1);
        entry.names().forEach(name -> keys.add(GlossaryKeys.of(name)));
        return keys;
    }

    /**
     * Adds an entry after the ones held; a key held already keeps its entry.
     *
     * @param entry the non-null entry
     */
    public void add(final GlossaryEntry entry) {
        byLemma.putIfAbsent(GlossaryKeys.of(entry.term()), entry);
        entry.forms().forEach(form -> byForm.putIfAbsent(GlossaryKeys.of(form), entry));
    }

    /**
     * The entry a key names.
     *
     * @param key a {@link GlossaryKeys} key
     * @return the entry whose lemma has the key, else the first that lists it as a form; or empty if none
     */
    public Optional<GlossaryEntry> find(final String key) {
        return Optional.ofNullable(byLemma.get(key)).or(() -> Optional.ofNullable(byForm.get(key)));
    }

    /**
     * The held entry another entry would duplicate: one its lemma or one of its forms names.
     *
     * @param entry the non-null incoming entry
     * @return the first held entry any of its keys names, or empty if none does
     */
    public Optional<GlossaryEntry> holderOf(final GlossaryEntry entry) {
        return keysOf(entry).stream().map(this::find).flatMap(Optional::stream).findFirst();
    }
}
