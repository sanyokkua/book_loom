package ua.bookloom.api.project;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The translation policy a project carries into every run, captured once on Names &amp; style and read at every
 * stage of the pipeline ({@code specs/book-brief/spec.md}).
 *
 * <p>The {@link #defaults(String)} values are what a newly opened book starts with — getting one wrong silently
 * changes every translation a person never explicitly configured.
 *
 * @param sourceLanguage the book's source language tag, or null for Auto: the model identifies the language
 * @param targetLanguage the chosen target language tag, or null when not yet chosen
 * @param genre the chosen genre, or null when not set
 * @param register the chosen tone register
 * @param voiceEra the chosen voice/era, or null when not set
 * @param audience the chosen audience, or null when not set
 * @param names the chosen name-rendering policy
 * @param foreignPassages the chosen foreign-passage policy
 * @param footnotes the chosen footnote policy
 * @param units the chosen units-of-measurement policy
 * @param balance the translation-freedom balance, from 0 (most literal) to 100 (most free)
 * @param alsoTranslate the auxiliary-text switches
 * @param dial the chosen speed/quality dial
 * @param narrator who narrates the book; {@link Narrator#unspecified()} when the person has not said
 * @param apostrophe the apostrophe character of the translated book, exactly one code point, or null for the target
 *     file's default
 * @param quoteStyle the quotation marks of the translated book, or null for the target file's default
 * @param defaultAddress the default form of address between characters, or null when not declared
 * @param conventions declared language choices by name; copied, never null
 * @param likely the fields the analysis filled that the person has not confirmed; copied, never null
 */
public record BookBrief(
        @Nullable String sourceLanguage,
        @Nullable String targetLanguage,
        @Nullable String genre,
        Register register,
        @Nullable String voiceEra,
        @Nullable String audience,
        NamePolicy names,
        ForeignPassagePolicy foreignPassages,
        FootnotePolicy footnotes,
        UnitPolicy units,
        int balance,
        AlsoTranslate alsoTranslate,
        QualityDial dial,
        Narrator narrator,
        @Nullable String apostrophe,
        @Nullable QuoteStyle quoteStyle,
        @Nullable Address defaultAddress,
        Map<String, String> conventions,
        Set<BriefField> likely) {

    private static final int MIN_BALANCE = 0;
    private static final int MAX_BALANCE = 100;
    private static final int DEFAULT_BALANCE = 55;

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public BookBrief {
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(foreignPassages, "foreignPassages");
        Objects.requireNonNull(footnotes, "footnotes");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(alsoTranslate, "alsoTranslate");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(narrator, "narrator");
        conventions = Map.copyOf(Objects.requireNonNull(conventions, "conventions"));
        likely = Set.copyOf(Objects.requireNonNull(likely, "likely"));
        if (apostrophe != null && apostrophe.codePointCount(0, apostrophe.length()) != 1) {
            throw new IllegalArgumentException(
                    "apostrophe must be exactly one character, but was '" + apostrophe + "'");
        }
        if (balance < MIN_BALANCE || balance > MAX_BALANCE) {
            throw new IllegalArgumentException("balance must be within [0,100], but was " + balance);
        }
    }

    /** A brief with no policy keys declared: the target file's defaults apply to all of them. */
    public BookBrief(
            @Nullable final String sourceLanguage,
            @Nullable final String targetLanguage,
            @Nullable final String genre,
            final Register register,
            @Nullable final String voiceEra,
            @Nullable final String audience,
            final NamePolicy names,
            final ForeignPassagePolicy foreignPassages,
            final FootnotePolicy footnotes,
            final UnitPolicy units,
            final int balance,
            final AlsoTranslate alsoTranslate,
            final QualityDial dial,
            final Narrator narrator) {
        this(
                sourceLanguage,
                targetLanguage,
                genre,
                register,
                voiceEra,
                audience,
                names,
                foreignPassages,
                footnotes,
                units,
                balance,
                alsoTranslate,
                dial,
                narrator,
                null,
                null,
                null,
                Map.of(),
                Set.of());
    }

    /** A brief with no narrator stated, which is what every brief made before the narrator existed holds. */
    public BookBrief(
            @Nullable final String sourceLanguage,
            @Nullable final String targetLanguage,
            @Nullable final String genre,
            final Register register,
            @Nullable final String voiceEra,
            @Nullable final String audience,
            final NamePolicy names,
            final ForeignPassagePolicy foreignPassages,
            final FootnotePolicy footnotes,
            final UnitPolicy units,
            final int balance,
            final AlsoTranslate alsoTranslate,
            final QualityDial dial) {
        this(
                sourceLanguage,
                targetLanguage,
                genre,
                register,
                voiceEra,
                audience,
                names,
                foreignPassages,
                footnotes,
                units,
                balance,
                alsoTranslate,
                dial,
                Narrator.unspecified());
    }

    /**
     * Returns the brief a newly opened book starts with, with the detected/chosen source language preselected.
     *
     * @param sourceLanguage the book's source language tag, or null when not yet detected
     * @return the default brief
     */
    public static BookBrief defaults(@Nullable final String sourceLanguage) {
        return new BookBrief(
                sourceLanguage,
                null,
                null,
                Register.NEUTRAL,
                null,
                null,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.KEEP,
                DEFAULT_BALANCE,
                AlsoTranslate.defaults(),
                QualityDial.BALANCED,
                Narrator.unspecified());
    }

    /**
     * Returns this brief with its two languages replaced, everything else kept.
     *
     * @param sourceLanguage the replacement source language tag, or null when none is chosen
     * @param targetLanguage the replacement target language tag, or null when none is chosen
     * @return a new brief with the supplied languages
     */
    public BookBrief withLanguages(@Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        return copy(sourceLanguage, targetLanguage, genre, voiceEra, audience, dial, narrator);
    }

    /**
     * Returns this brief with its narrator replaced, everything else kept.
     *
     * @param chosen the non-null narrator
     * @return a new brief with the narrator
     */
    public BookBrief withNarrator(final Narrator chosen) {
        return copy(sourceLanguage, targetLanguage, genre, voiceEra, audience, dial, chosen);
    }

    /**
     * Returns this brief with its genre replaced, everything else kept.
     *
     * @param genre the replacement, or null to clear it
     * @return a new brief with the genre
     */
    public BookBrief withGenre(@Nullable final String genre) {
        return copy(sourceLanguage, targetLanguage, genre, voiceEra, audience, dial, narrator);
    }

    /**
     * Returns this brief with its voice and era notes replaced, everything else kept.
     *
     * @param voiceEra the replacement, or null to clear it
     * @return a new brief with the voice and era notes
     */
    public BookBrief withVoiceEra(@Nullable final String voiceEra) {
        return copy(sourceLanguage, targetLanguage, genre, voiceEra, audience, dial, narrator);
    }

    /**
     * Returns this brief with its audience replaced, everything else kept.
     *
     * @param audience the replacement, or null to clear it
     * @return a new brief with the audience
     */
    public BookBrief withAudience(@Nullable final String audience) {
        return copy(sourceLanguage, targetLanguage, genre, voiceEra, audience, dial, narrator);
    }

    /**
     * Returns this brief with its quality dial replaced, everything else kept.
     *
     * @param dial the replacement
     * @return a new brief with the quality dial
     */
    public BookBrief withDial(final QualityDial dial) {
        return copy(sourceLanguage, targetLanguage, genre, voiceEra, audience, dial, narrator);
    }

    /**
     * Returns this brief with its apostrophe replaced, everything else kept.
     *
     * @param apostrophe exactly one code point, or null for the target file's default
     * @return a new brief with the apostrophe
     */
    public BookBrief withApostrophe(@Nullable final String apostrophe) {
        return keys(apostrophe, quoteStyle, defaultAddress, conventions, likely);
    }

    /**
     * Returns this brief with its quotation marks replaced, everything else kept.
     *
     * @param quoteStyle the replacement, or null for the target file's default
     * @return a new brief with the quotation marks
     */
    public BookBrief withQuoteStyle(@Nullable final QuoteStyle quoteStyle) {
        return keys(apostrophe, quoteStyle, defaultAddress, conventions, likely);
    }

    /**
     * Returns this brief with its default form of address replaced, everything else kept.
     *
     * @param defaultAddress the replacement, or null when not declared
     * @return a new brief with the address
     */
    public BookBrief withDefaultAddress(@Nullable final Address defaultAddress) {
        return keys(apostrophe, quoteStyle, defaultAddress, conventions, likely);
    }

    /**
     * Returns this brief with its declared conventions replaced, everything else kept.
     *
     * @param conventions the replacement; non-null
     * @return a new brief with the conventions
     */
    public BookBrief withConventions(final Map<String, String> conventions) {
        return keys(apostrophe, quoteStyle, defaultAddress, conventions, likely);
    }

    /**
     * Returns this brief with its set of unconfirmed fields replaced, everything else kept.
     *
     * @param fields the fields the analysis filled that the person has not confirmed; non-null
     * @return a new brief with the set
     */
    public BookBrief withLikely(final Set<BriefField> fields) {
        return keys(apostrophe, quoteStyle, defaultAddress, conventions, fields);
    }

    private BookBrief keys(
            @Nullable final String apostrophe,
            @Nullable final QuoteStyle quoteStyle,
            @Nullable final Address defaultAddress,
            final Map<String, String> conventions,
            final Set<BriefField> likely) {
        return new BookBrief(
                sourceLanguage,
                targetLanguage,
                genre,
                register,
                voiceEra,
                audience,
                names,
                foreignPassages,
                footnotes,
                units,
                balance,
                alsoTranslate,
                dial,
                narrator,
                apostrophe,
                quoteStyle,
                defaultAddress,
                conventions,
                likely);
    }

    private BookBrief copy(
            @Nullable final String sourceLanguage,
            @Nullable final String targetLanguage,
            @Nullable final String genre,
            @Nullable final String voiceEra,
            @Nullable final String audience,
            final QualityDial dial,
            final Narrator narrator) {
        return new BookBrief(
                sourceLanguage,
                targetLanguage,
                genre,
                register,
                voiceEra,
                audience,
                names,
                foreignPassages,
                footnotes,
                units,
                balance,
                alsoTranslate,
                dial,
                narrator,
                apostrophe,
                quoteStyle,
                defaultAddress,
                conventions,
                likely);
    }
}
