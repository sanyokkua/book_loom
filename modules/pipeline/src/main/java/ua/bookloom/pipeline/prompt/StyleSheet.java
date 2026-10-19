package ua.bookloom.pipeline.prompt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;
import ua.bookloom.util.hash.HashUtil;

/**
 * The style guidance every draft and repair carries, derived from the Book Brief without a model call so it is free
 * and reproducible.
 *
 * @param text the guidance as sent to the model; this is also what a context snapshot stores
 * @param hash the SHA-256 of {@code text}, so a run can tell whether the guidance changed
 */
@Slf4j
public record StyleSheet(String text, String hash) {

    private static final int DEFAULT_BALANCE = 55;

    /** Validates the components. */
    public StyleSheet {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hash, "hash");
    }

    /**
     * Derives the sheet: an all-default brief renders the long-standing default line alone, any other brief that line
     * followed by its free-text entries and one phrase per choice.
     *
     * @param brief the project's brief
     * @return the sheet, identical for identical briefs
     */
    public static StyleSheet from(final BookBrief brief) {
        Objects.requireNonNull(brief, "brief");
        final StylePhrases phrases = StylePhrases.bundled();
        final int band = StylePhrases.bandOf(brief.balance());
        final boolean isDefault = isAllDefault(brief);
        final String text = isDefault ? phrases.defaultLine() : String.join("\n", lines(brief, phrases, band));
        final StyleSheet sheet = ofText(text);
        log.debug(
                "Derived style sheet register={} names={} foreign={} footnotes={} units={} balance={} band={} "
                        + "genreSet={} voiceEraSet={} audienceSet={} allDefault={} hash={}",
                brief.register(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                band,
                !isBlank(brief.genre()),
                !isBlank(brief.voiceEra()),
                !isBlank(brief.audience()),
                isDefault,
                sheet.hash());
        log.trace("Style sheet text {}", sheet.text());
        return sheet;
    }

    /**
     * Rebuilds a sheet from its text alone, as a context snapshot records it, so a retry sends the guidance its first
     * draft was sent even after the brief changed.
     *
     * @param text the non-null guidance text
     * @return the sheet with the text's hash
     */
    public static StyleSheet ofText(final String text) {
        Objects.requireNonNull(text, "text");
        return new StyleSheet(text, HashUtil.sha256Hex(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Returns the system message's foreign-passage rule for a policy.
     *
     * @param policy the brief's foreign-passage policy
     * @param sourceLanguage the source language as the prompt describes it
     * @return the sentence, with the source language substituted
     */
    public static String foreignPassageRule(final ForeignPassagePolicy policy, final String sourceLanguage) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        return StylePhrases.bundled().phrase(policy).replace("{source}", sourceLanguage);
    }

    private static List<String> lines(final BookBrief brief, final StylePhrases phrases, final int band) {
        final List<String> lines = new ArrayList<>();
        lines.add(phrases.defaultLine());
        addEntry(lines, "Genre: ", brief.genre());
        addEntry(lines, "Narrative voice / era: ", brief.voiceEra());
        addEntry(lines, "Audience: ", brief.audience());
        lines.add(phrases.phrase(brief.register()));
        lines.add(phrases.balance(band));
        lines.add(phrases.phrase(brief.names()));
        lines.add(phrases.phrase(brief.footnotes()));
        lines.add(phrases.phrase(brief.units()));
        addEntry(lines, "", phrases.narrator(brief.narrator()));
        return lines;
    }

    private static void addEntry(final List<String> lines, final String label, @Nullable final String value) {
        if (!isBlank(value)) {
            lines.add(label + value);
        }
    }

    private static boolean isBlank(@Nullable final String value) {
        return value == null || value.isBlank();
    }

    private static boolean isAllDefault(final BookBrief brief) {
        return brief.register() == Register.NEUTRAL
                && brief.names() == NamePolicy.TRANSLITERATE
                && brief.foreignPassages() == ForeignPassagePolicy.KEEP
                && brief.footnotes() == FootnotePolicy.TRANSLATE
                && brief.units() == UnitPolicy.KEEP
                && brief.balance() == DEFAULT_BALANCE
                && isBlank(brief.genre())
                && isBlank(brief.voiceEra())
                && isBlank(brief.audience())
                && brief.narrator().person() == NarratorPerson.UNSPECIFIED;
    }
}
