package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The translation policy a project carries into every run, captured once on Names &amp; style and read at every
 * stage of the pipeline ({@code specs/book-brief/spec.md}).
 *
 * <p>The {@link #defaults(String)} values are what a newly opened book starts with — getting one wrong silently
 * changes every translation a person never explicitly configured.
 *
 * @param sourceLanguage the book's source language tag, or null when not yet detected/chosen
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
        QualityDial dial) {

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
        if (balance < MIN_BALANCE || balance > MAX_BALANCE) {
            throw new IllegalArgumentException("balance must be within [0,100], but was " + balance);
        }
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
                QualityDial.BALANCED);
    }

    /**
     * Returns this brief with its two languages replaced, everything else kept.
     *
     * @param sourceLanguage the replacement source language tag, or null when none is chosen
     * @param targetLanguage the replacement target language tag, or null when none is chosen
     * @return a new brief with the supplied languages
     */
    public BookBrief withLanguages(@Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
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
                dial);
    }
}
