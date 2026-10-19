package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Refusal;
import ua.bookloom.pipeline.glossary.TermEvidence.Evidence;
import ua.bookloom.pipeline.glossary.TermReading.Reading;

/**
 * One name's line in the suggestion call (15i.N6): its type and gender, whether its reading calls it a speaking name or
 * a brand, what the glossary already renders of its person, two sentences of the book, and for a retry the rendering
 * that was refused and why.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionLines {

    /** An example sentence longer than this is cut, so the batch stays short. */
    static final int MAX_EXAMPLE_CHARS = 120;

    private static final String SEPARATOR = " — ";

    /**
     * The line of one entry.
     *
     * @param entry the entry asked about
     * @param evidence how the book uses it
     * @param reading its reading, or null when it was not read
     * @param cluster what is already rendered of its person, when something is
     * @param refusal the refused rendering when this is the retry, else null
     * @return one line that starts with {@code "- "} and the term
     */
    static String of(
            final GlossaryEntry entry,
            final Evidence evidence,
            @Nullable final Reading reading,
            final Optional<String> cluster,
            @Nullable final Refusal refusal) {
        final List<String> parts = new ArrayList<>();
        parts.add(typeOf(entry.type()) + genderOf(entry));
        if (reading != null && reading.speaking()) {
            parts.add("speaking name");
        }
        if (reading != null && reading.brand()) {
            parts.add("brand");
        }
        cluster.ifPresent(parts::add);
        evidence.examples().forEach(example -> parts.add("\"" + cut(example) + "\""));
        if (refusal != null) {
            parts.add(refusedNote(refusal));
        }
        return "- " + entry.term() + SEPARATOR + String.join(SEPARATOR, parts);
    }

    private static String refusedNote(final Refusal refusal) {
        return "refused: \"" + refusal.rendering() + "\" (" + refusal.why().reason() + "); give another";
    }

    private static String genderOf(final GlossaryEntry entry) {
        return entry.gender() == Gender.UNKNOWN
                ? ""
                : ", " + entry.gender().name().toLowerCase(Locale.ROOT);
    }

    private static String cut(final String example) {
        return example.length() > MAX_EXAMPLE_CHARS ? example.substring(0, MAX_EXAMPLE_CHARS) + "…" : example;
    }

    private static String typeOf(final TermType type) {
        return switch (type) {
            case CHARACTER -> "person";
            case PLACE -> "place";
            case TERM -> "term";
            case TITLE -> "title";
            case OTHER -> "other";
        };
    }
}
