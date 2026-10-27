package ua.bookloom.ui;

import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;

/**
 * The invented tables and the report the automatic names pass tests share: {@code Mid} is added, {@code Hale} is
 * dropped as a non-name and {@code Well} is typed; {@code pentacle} is added and {@code table} is dropped.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AutoNamesFixture {

    public static final String PROJECT = "p1";
    public static final GlossaryEntry WELL = entry("e1", "Well", TermType.OTHER, Gender.UNKNOWN);
    public static final GlossaryEntry WELL_TYPED = entry("e1", "Well", TermType.CHARACTER, Gender.FEMALE);
    public static final GlossaryEntry HALE = entry("e2", "Hale", TermType.OTHER, Gender.UNKNOWN);
    public static final GlossaryEntry MID = entry("e3", "Mid", TermType.CHARACTER, Gender.MALE);
    public static final LexiconEntry TABLE = LexiconEntry.of(PROJECT, "table");
    public static final LexiconEntry PENTACLE = LexiconEntry.of(PROJECT, "pentacle");

    /**
     * A character of the fixture project, as the conformance glossary lists them: with the two longest gender labels,
     * a suggested female and a person the book gives no evidence for, among them.
     */
    public static GlossaryEntry character(final String id, final String term, final Gender gender) {
        return new GlossaryEntry(id, PROJECT, term, null, TermType.CHARACTER, gender, false);
    }

    private static GlossaryEntry entry(final String id, final String term, final TermType type, final Gender gender) {
        return new GlossaryEntry(id, PROJECT, term, null, type, gender, false);
    }

    /** {@return what the pass reports for the tables {@code Well}, {@code Hale} and {@code table}} */
    public static NamesPassReport report() {
        final EntryChanges<GlossaryEntry> names = new EntryChanges<>(
                List.of(MID),
                List.of(new EntryChanges.Removal<>(HALE, "not a name")),
                List.of(new EntryChanges.Change<>(WELL, WELL_TYPED, null)));
        final EntryChanges<LexiconEntry> terms =
                new EntryChanges<>(List.of(PENTACLE), List.of(new EntryChanges.Removal<>(TABLE, null)), List.of());
        return new NamesPassReport(names, terms, 7, Duration.ofSeconds(90));
    }
}
